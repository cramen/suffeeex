package ru.cramen.suffeeex.core.node

import ru.cramen.suffeeex.core.ExpressionException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/** JVM stack/local categories — the only thing bytecode needs to know about a type. */
enum class StackCategory { INT, LONG, FLOAT, DOUBLE, REFERENCE }

/**
 * How a type is represented in bytecode: its descriptor, stack/local
 * category and boxing route. Registering a [TypeEmission] for a new type
 * (see [TypeEmissions.register]) makes it usable on bytecode backends
 * without touching core.
 */
interface TypeEmission {
    val type: KClass<*>

    /** JVM descriptor of the type: "I", "Ljava/math/BigDecimal;". */
    val descriptor: String

    val category: StackCategory

    /**
     * Internal name of the boxing wrapper ("java/lang/Integer");
     * null for reference types, where boxing is a no-op.
     */
    val wrapperInternalName: String?

    /** No-arg unboxing method of the wrapper ("intValue"); null for reference types. */
    val unboxMethod: String?

    /** Pushes a compile-time constant of [type]; the default goes through LDC. */
    fun pushConstant(emission: Emission, value: Any) {
        emission.ldc(value)
    }
}

/**
 * Registry of [TypeEmission]s. An instance resolves a type through its own
 * registrations, then the [parent]'s, then an automatic reference-type
 * fallback — so an extension registers its types into the registry of its
 * own `ExtensionRegistry` without affecting other compilers. [DEFAULT]
 * holds the built-in types (Int, Long, Float, Double, Boolean, String) and
 * is the parent of every `ExtensionRegistry`'s registry.
 */
class TypeEmissions(private val parent: TypeEmissions? = null) {
    private val registered = ConcurrentHashMap<KClass<*>, TypeEmission>()

    fun register(support: TypeEmission) {
        registered[support.type] = support
    }

    /**
     * The registered [TypeEmission] for [type]: own registrations first,
     * then the [parent] chain, then an automatic reference-type fallback
     * (descriptor "L<internal name>;", category [StackCategory.REFERENCE],
     * no wrapper; [TypeEmission.pushConstant] throws a clear
     * [ExpressionException]).
     */
    fun of(type: KClass<*>): TypeEmission =
        registered[type] ?: parent?.of(type) ?: referenceFallback(type)

    companion object {
        /** The built-in types: Int, Long, Float, Double, Boolean, String. */
        val DEFAULT: TypeEmissions = TypeEmissions().apply {
            register(primitive(Int::class, "I", StackCategory.INT, "java/lang/Integer", "intValue"))
            register(primitive(Long::class, "J", StackCategory.LONG, "java/lang/Long", "longValue"))
            register(primitive(Float::class, "F", StackCategory.FLOAT, "java/lang/Float", "floatValue"))
            register(primitive(Double::class, "D", StackCategory.DOUBLE, "java/lang/Double", "doubleValue"))
            // booleans are int 0/1 on the stack, and LDC cannot push them
            register(object : TypeEmission {
                override val type = Boolean::class
                override val descriptor = "Z"
                override val category = StackCategory.INT
                override val wrapperInternalName = "java/lang/Boolean"
                override val unboxMethod = "booleanValue"
                override fun pushConstant(emission: Emission, value: Any) {
                    emission.ldc(if (value as Boolean) 1 else 0)
                }
            })
            register(reference(String::class))
        }

        private fun primitive(
            type: KClass<*>,
            descriptor: String,
            category: StackCategory,
            wrapperInternalName: String,
            unboxMethod: String,
        ) = object : TypeEmission {
            override val type = type
            override val descriptor = descriptor
            override val category = category
            override val wrapperInternalName = wrapperInternalName
            override val unboxMethod = unboxMethod
        }

        private fun reference(type: KClass<*>) = object : TypeEmission {
            override val type = type
            override val descriptor = "L" + type.java.name.replace('.', '/') + ";"
            override val category = StackCategory.REFERENCE
            override val wrapperInternalName: String? = null
            override val unboxMethod: String? = null
        }

        private fun referenceFallback(type: KClass<*>) = object : TypeEmission {
            private val delegate = reference(type)
            override val type = type
            override val descriptor = delegate.descriptor
            override val category = delegate.category
            override val wrapperInternalName: String? = null
            override val unboxMethod: String? = null
            override fun pushConstant(emission: Emission, value: Any) {
                throw ExpressionException(
                    "cannot push a constant of type ${type.simpleName}:" +
                        " register a TypeEmission for it (ExtensionRegistry.registerTypeEmission)"
                )
            }
        }
    }
}
