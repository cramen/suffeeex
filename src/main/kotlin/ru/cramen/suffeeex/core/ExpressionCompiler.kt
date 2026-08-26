package ru.cramen.suffeeex.core

import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.core.backend.SpecializedBackend
import ru.cramen.suffeeex.core.backend.asm.AsmBackend
import ru.cramen.suffeeex.core.backend.specializedSignature
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.syntax.SyntaxParser
import ru.cramen.suffeeex.core.token.Tokenizer
import kotlin.reflect.KClass

class ExpressionCompiler(registry: ExtensionRegistry) {
    private val tokenizer = Tokenizer(registry.tokenParsers)
    private val syntaxParser = SyntaxParser(registry)

    constructor(vararg extensions: SyntaxExtension) : this(
        ExtensionRegistry().also { registry -> extensions.forEach { it.register(registry) } }
    )

    fun compile(
        source: String,
        varTypes: Map<String, KClass<*>> = emptyMap(),
        backend: ExpressionBackend = AsmBackend,
    ): Expression = backend.compile(syntaxParser.parse(tokenizer.tokenize(source), varTypes))

    /**
     * Compiles [source] into an implementation of the fun interface [target]:
     * the single abstract method's parameters are the expression variables
     * (by name), its return type must match the expression type (a primitive
     * or its wrapper are both accepted).
     */
    fun <T : Any> compile(
        source: String,
        target: KClass<T>,
        backend: ExpressionBackend = AsmBackend,
    ): T {
        if (backend !is SpecializedBackend) {
            throw ExpressionException("backend $backend does not support specialized compilation")
        }
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
        return backend.compile(root, target) as T
    }
}
