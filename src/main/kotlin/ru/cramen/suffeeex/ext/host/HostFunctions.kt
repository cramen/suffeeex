package ru.cramen.suffeeex.ext.host

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.FunctionParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.LOW_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.ext.math.function.IdentifierTokenType
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaMethod

/**
 * Invocation of a user-provided host function. The call plan (static vs
 * object member, declaring class, method name) is resolved once at compile
 * time; evaluation either reflects via [KFunction.call] (composition
 * backend) or invokes the resolved JVM method directly (bytecode backends).
 */
class HostFunctionNode(
    val name: String,
    val args: List<TypedNode>,
    override val type: KClass<*>,
    private val function: KFunction<*>,
    private val receiver: Any?,
    private val owner: KClass<*>,
    private val methodName: String,
    private val paramTypes: List<KClass<*>>,
    /** Static field (owner to name) holding the singleton receiver; null for static/top-level functions. */
    private val instanceField: Pair<KClass<*>, String>?,
) : TypedNode {
    // Bound object-member references (Rounding::half) carry their receiver
    // implicitly — they have no INSTANCE parameter and must be called with
    // value arguments only; unbound ones take the object instance first.
    private val needsReceiver =
        receiver != null && function.parameters.any { it.kind == KParameter.Kind.INSTANCE }

    override fun build(): Expression {
        val built = args.map { it.build() }
        return Expression { c ->
            val values = built.map { it.eval(c) }.toTypedArray()
            if (needsReceiver) function.call(receiver, *values) else function.call(*values)
        }
    }

    override fun emit(emission: Emission) {
        val field = instanceField
        if (field != null) emission.getStaticField(field.first, field.second, owner)
        args.forEach { emission.push(it) }
        if (field != null) {
            emission.invokeVirtual(owner, methodName, paramTypes, type)
        } else {
            emission.invokeStatic(owner, methodName, paramTypes, type)
        }
    }
}

private fun KFunction<*>.unsupported(what: String, name: String): Nothing =
    throw ExpressionException("host function '$name': $what is not supported")

private fun KType.requireClass(name: String): KClass<*> {
    if (isMarkedNullable) {
        throw ExpressionException("host function '$name': nullable types are not supported, got $this")
    }
    return classifier as? KClass<*>
        ?: throw ExpressionException("host function '$name': cannot map type $this to a class")
}

private class HostFunctionParser(
    override val name: String,
    private val function: KFunction<*>,
) : FunctionParser {
    init {
        if (function.isSuspend) function.unsupported("suspend functions", name)
        if (function.typeParameters.isNotEmpty()) function.unsupported("generic functions", name)
        if (function.parameters.any { it.isVararg }) function.unsupported("vararg functions", name)
        if (function.parameters.any { it.isOptional }) function.unsupported("default arguments (arity is exact)", name)
    }

    private val valueParameters = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
    private val paramTypes = valueParameters.map { it.type.requireClass(name) }
    private val returnType = function.returnType.requireClass(name)

    override val minArgs = paramTypes.size
    override val maxArgs = paramTypes.size

    // Static/top-level functions call without a receiver; Kotlin object and
    // companion members call on the singleton. Instance methods of regular
    // classes are rejected: v1 supports static/object only.
    private val method = function.javaMethod
        ?: throw ExpressionException("host function '$name': no JVM method found")
    private val receiver: Any?
    private val owner: KClass<*>
    private val instanceField: Pair<KClass<*>, String>?

    init {
        val declaringClass = method.declaringClass
        receiver = if (Modifier.isStatic(method.modifiers)) {
            null
        } else {
            declaringClass.kotlin.objectInstance
                ?: throw ExpressionException(
                    "host function '$name': instance methods are not supported; v1 supports static/object only"
                )
        }
        owner = declaringClass.kotlin
        instanceField = receiver?.let { instance ->
            // plain objects expose INSTANCE on their own class; the companion
            // singleton lives in a static field (named after the companion)
            // on the enclosing class — resolve it instead of guessing
            val field = generateSequence(declaringClass) { it.enclosingClass }
                .flatMap { it.declaredFields.asSequence() }
                .firstOrNull { f ->
                    Modifier.isStatic(f.modifiers) && f.type == declaringClass && f.get(null) === instance
                }
                ?: throw ExpressionException("host function '$name': cannot locate the object instance field")
            field.declaringClass.kotlin to field.name
        }
    }

    override fun compile(args: List<TypedNode>): TypedNode {
        args.forEachIndexed { i, arg ->
            val expected = paramTypes[i]
            if (arg.type != expected) {
                throw ExpressionException(
                    "function '$name' expects argument ${i + 1} to be ${expected.simpleName}, got ${arg.type.simpleName}"
                )
            }
        }
        return HostFunctionNode(name, args, returnType, function, receiver, owner, method.name, paramTypes, instanceField)
    }
}

/** Registers [function] under [name] so expressions can call it as `name(args...)`. */
fun ExtensionRegistry.registerHostFunction(name: String, function: KFunction<*>) {
    registerFunction(HostFunctionParser(name, function))
}

/**
 * Makes Kotlin/JVM functions callable from expressions:
 * `HostFunctionsExtension("vat" to ::calcVat)`.
 *
 * Registers the shared identifier token (function-call syntax needs a
 * bracket extension, e.g. [ru.cramen.suffeeex.ext.math.bracket.BracketExtension]).
 */
class HostFunctionsExtension(vararg val functions: Pair<String, KFunction<*>>) : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        // the identifier token type is shared with MathFunctionsExtension /
        // LogicExtension so the extensions compose (an identical duplicate
        // registration is harmless)
        registry.registerTokenParser(
            RegexpTokenParser(IdentifierTokenType, Regex("[a-zA-Z_][a-zA-Z0-9_]*"), LOW_TOKEN_PRIORITY)
        )
        registry.registerFunctionNameTokenType(IdentifierTokenType)
        functions.forEach { (name, function) -> registry.registerHostFunction(name, function) }
    }
}
