package ru.cramen.suffeeex.core

import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.core.backend.SpecializedBackend
import ru.cramen.suffeeex.core.backend.asm.AsmBackend
import ru.cramen.suffeeex.core.backend.specializedSignature
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.syntax.SyntaxParser
import ru.cramen.suffeeex.core.token.Tokenizer
import java.lang.ref.SoftReference
import kotlin.reflect.KClass

class ExpressionCompiler(
    /**
     * The registry this compiler was built from. Public so that tooling
     * (e.g. input suggesters) can be driven by the same extension
     * configuration as parsing, without core depending on such tooling.
     */
    val registry: ExtensionRegistry,
) {
    private val tokenizer = Tokenizer(registry.tokenParsers)
    private val syntaxParser = SyntaxParser(registry)

    private data class ExpressionCacheKey(
        val source: String,
        val varTypes: Map<String, KClass<*>>,
        val backend: ExpressionBackend,
    )

    private data class SpecializedCacheKey(
        val source: String,
        val target: KClass<*>,
        val backend: ExpressionBackend,
    )

    // compiled expressions are stateless, so the same key can safely share
    // one instance (and one generated class) across all callers; soft
    // references let the GC drop entries (with their classloaders) under
    // memory pressure — the next compile simply regenerates them; the LRU
    // bound keeps keys and cleared wrappers from accumulating when
    // expression sources are unbounded (e.g. generated on the fly)
    private val expressionCache = SoftLruCache<ExpressionCacheKey, Expression>()
    private val specializedCache = SoftLruCache<SpecializedCacheKey, Any>()

    /**
     * A small thread-safe LRU cache with soft-referenced values: cleared
     * entries are dropped and recomputed on access, and the eldest entry is
     * evicted once [maxSize] is exceeded, so unbounded distinct sources
     * cannot accumulate keys forever.
     */
    private class SoftLruCache<K, V : Any>(private val maxSize: Int = 1024) {
        private val map = object : LinkedHashMap<K, SoftReference<V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, SoftReference<V>>): Boolean =
                size > maxSize
        }

        @Synchronized
        fun computeLive(key: K, mapping: (K) -> V): V {
            map[key]?.get()?.let { return it }
            val value = mapping(key)
            map[key] = SoftReference(value)
            return value
        }
    }

    constructor(vararg extensions: SyntaxExtension) : this(
        ExtensionRegistry().also { registry -> extensions.forEach { it.register(registry) } }
    )

    /**
     * Compiles [source] into a ready [Expression]. Results are cached by
     * (source, varTypes, backend): compiling the same key twice returns the
     * same instance (compiled expressions are stateless). The cache is a
     * bounded LRU (1024 entries) of soft references — under memory pressure
     * or past the bound an entry is dropped, and the next compile simply
     * regenerates it. [parseTree] and [compileTree] are not cached.
     */
    fun compile(
        source: String,
        varTypes: Map<String, KClass<*>> = emptyMap(),
        backend: ExpressionBackend = AsmBackend,
    ): Expression = expressionCache.computeLive(ExpressionCacheKey(source, varTypes, backend)) {
        compileTree(parseTree(source, varTypes), backend)
    }

    /**
     * Tokenizes and parses [source] into the typed node tree, without
     * compiling it. Useful when the tree itself is needed, e.g. for
     * symbolic transformations before compilation.
     */
    fun parseTree(source: String, varTypes: Map<String, KClass<*>> = emptyMap()): TypedNode =
        syntaxParser.parse(tokenizer.tokenize(source), varTypes)

    /** Compiles an already parsed node tree with [backend]. */
    fun compileTree(root: TypedNode, backend: ExpressionBackend = AsmBackend): Expression =
        backend.compile(root, registry.typeEmissions)

    /**
     * Compiles [source] into an implementation of the fun interface [target]:
     * the single abstract method's parameters are the expression variables
     * (by name), its return type must match the expression type (a primitive
     * or its wrapper are both accepted). Cached by (source, target, backend)
     * with the same bounded-LRU + soft-reference semantics as the other
     * [compile] overload: compiling the same key twice returns the same
     * instance while it is reachable and within the cache bound; a dropped
     * entry is regenerated on the next compile.
     */
    fun <T : Any> compile(
        source: String,
        target: KClass<T>,
        backend: ExpressionBackend = AsmBackend,
    ): T {
        if (backend !is SpecializedBackend) {
            throw ExpressionException("backend $backend does not support specialized compilation")
        }
        @Suppress("UNCHECKED_CAST")
        return specializedCache.computeLive(SpecializedCacheKey(source, target, backend)) {
            compileSpecialized(source, target, backend)
        } as T
    }

    private fun <T : Any> compileSpecialized(
        source: String,
        target: KClass<T>,
        backend: SpecializedBackend,
    ): T {
        val signature = specializedSignature(target)
        val varTypes = signature.parameters.associate { it.name to it.type }

        // records which variables the frontend actually resolved, so that
        // declared-but-unused parameters can be rejected below
        val usedVariables = mutableSetOf<String>()
        val recordingVarTypes = object : Map<String, KClass<*>> by varTypes {
            override fun get(key: String): KClass<*>? = varTypes[key]?.also { usedVariables += key }
        }
        val root = syntaxParser.parse(tokenizer.tokenize(source), recordingVarTypes)

        val unusedParameters = varTypes.keys - usedVariables
        if (unusedParameters.isNotEmpty()) {
            throw ExpressionException(
                "parameters of ${target.simpleName}.${signature.methodName} are not used" +
                    " in the expression: ${unusedParameters.joinToString()}"
            )
        }
        if (root.type != signature.returnType) {
            throw ExpressionException(
                "return type mismatch: the expression evaluates to ${root.type.simpleName}," +
                    " but ${target.simpleName}.${signature.methodName} returns ${signature.returnType.simpleName}"
            )
        }
        @Suppress("UNCHECKED_CAST")
        return backend.compile(root, target, registry.typeEmissions) as T
    }
}
