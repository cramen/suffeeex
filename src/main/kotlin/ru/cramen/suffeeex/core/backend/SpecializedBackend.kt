package ru.cramen.suffeeex.core.backend

import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.TypeEmissions
import ru.cramen.suffeeex.core.node.TypedNode
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaMethod

/**
 * Backend that can compile the node tree against a user-provided fun
 * interface: the generated/implemented object's single abstract method
 * takes the expression variables as its parameters.
 */
interface SpecializedBackend {
    fun compile(root: TypedNode, target: KClass<*>, types: TypeEmissions = TypeEmissions.DEFAULT): Any
}

internal class SpecializedParameter(val name: String, val type: KClass<*>, val slot: Int)

/**
 * The single-abstract-method contract of a specialized compile target.
 * [returnType] is the normalized (wrapper unmapped to primitive) type;
 * [referenceReturn] tells whether the JVM method descriptor uses the
 * reference form (wrapper, nullable primitive, or String).
 */
internal class SpecializedSignature(
    val methodName: String,
    val parameters: List<SpecializedParameter>,
    val returnType: KClass<*>,
    val referenceReturn: Boolean,
)

private val PRIMITIVE_CLASS_TO_KCLASS: Map<Class<*>, KClass<*>> = mapOf(
    Integer.TYPE to Int::class,
    java.lang.Long.TYPE to Long::class,
    java.lang.Float.TYPE to Float::class,
    java.lang.Double.TYPE to Double::class,
    java.lang.Boolean.TYPE to Boolean::class,
)

private val WRAPPER_CLASS_TO_KCLASS: Map<Class<*>, KClass<*>> = mapOf(
    java.lang.Integer::class.java to Int::class,
    java.lang.Long::class.java to Long::class,
    java.lang.Float::class.java to Float::class,
    java.lang.Double::class.java to Double::class,
    java.lang.Boolean::class.java to Boolean::class,
)

/**
 * Extracts and validates the fun-interface contract of [target].
 *
 * JVM-level types come from the abstract method itself (Kotlin maps
 * wrapper classes to primitives, so `Int?` / `java.lang.Integer` are only
 * distinguishable there); parameter names come from kotlin-reflect, so
 * Kotlin sources work out of the box and Java interfaces need `-parameters`.
 */
internal fun specializedSignature(target: KClass<*>): SpecializedSignature {
    if (!target.java.isInterface) {
        throw ExpressionException(
            "specialized compile target must be an interface, got ${target.qualifiedName}"
        )
    }
    // java reflection for the abstract-method check: Object methods are not
    // listed for interfaces, and default methods are not abstract
    val abstractMethods = target.java.methods.filter { Modifier.isAbstract(it.modifiers) }
    if (abstractMethods.size != 1) {
        throw ExpressionException(
            "specialized compile target must have exactly one abstract method," +
                " got ${abstractMethods.size} in ${target.qualifiedName}"
        )
    }
    val javaMethod = abstractMethods.single()
    val function = target.members.filterIsInstance<KFunction<*>>().firstOrNull { it.javaMethod == javaMethod }
        ?: throw ExpressionException("cannot inspect ${target.qualifiedName}.${javaMethod.name} via kotlin-reflect")

    val valueParameters = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
    var slot = 1 // slot 0 is 'this'
    val parameters = valueParameters.mapIndexed { index, parameter ->
        val name = parameter.name
            ?: throw ExpressionException(
                "parameter names are unavailable for ${target.qualifiedName}.${function.name};" +
                    " Java interfaces must be compiled with '-parameters'"
            )
        val javaType = javaMethod.parameterTypes[index]
        // any reference type is accepted (loaded/stored as an object);
        // wrappers are not: their Kotlin type maps to a primitive KClass
        val type = PRIMITIVE_CLASS_TO_KCLASS[javaType]
            ?: if (javaType == String::class.java) String::class
            else if (!javaType.isPrimitive && WRAPPER_CLASS_TO_KCLASS[javaType] == null) javaType.kotlin
            else null
            ?: throw ExpressionException(
                "unsupported type of parameter '$name': ${parameter.type};" +
                    " supported: non-nullable Int, Long, Float, Double, Boolean, String," +
                    " or a reference type (e.g. a data class for property access)"
            )
        val current = slot
        slot += if (type == Long::class || type == Double::class) 2 else 1
        SpecializedParameter(name, type, current)
    }

    val javaReturnType = javaMethod.returnType
    val returnType = PRIMITIVE_CLASS_TO_KCLASS[javaReturnType]
        ?: WRAPPER_CLASS_TO_KCLASS[javaReturnType]
        ?: if (javaReturnType == String::class.java) String::class else null
        ?: throw ExpressionException(
            "unsupported return type of ${target.qualifiedName}.${function.name}: $javaReturnType;" +
                " supported: Int, Long, Float, Double, Boolean, String or their wrappers"
        )
    val referenceReturn = !javaReturnType.isPrimitive

    return SpecializedSignature(function.name, parameters, returnType, referenceReturn)
}
