package ru.cramen.suffeeex.ext.math.function

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.NUMERIC_TYPES
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.FunctionParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.LOW_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.core.token.TokenType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.reflect.KClass

object IdentifierTokenType : TokenType()

/** Explicit numeric conversion: accepts any numeric argument, result type is the target. */
class ConvertNumericNode(val target: KClass<*>, val arg: TypedNode) : TypedNode {
    override val type: KClass<*> = target

    private val convert: (Number) -> Number = when (target) {
        Int::class -> Number::toInt
        Long::class -> Number::toLong
        Float::class -> Number::toFloat
        Double::class -> Number::toDouble
        else -> throw ExpressionException("unsupported conversion target: ${target.simpleName}")
    }

    override fun build(): Expression {
        val e = arg.build()
        return Expression { c -> convert(e.eval(c) as Number) }
    }

    override fun emit(emission: Emission) {
        emission.push(arg)
        emission.convertNumeric(arg.type, target)
    }
}

/**
 * Invocation of a math function with statically known argument and result
 * types. The implementation is chosen once at compile time.
 */
class MathFunctionNode(
    val name: String,
    val args: List<TypedNode>,
    override val type: KClass<*>,
    private val impl: (List<Any?>) -> Any?,
) : TypedNode {
    override fun build(): Expression {
        val built = args.map { it.build() }
        return Expression { c -> impl(built.map { arg -> arg.eval(c) }) }
    }

    override fun emit(emission: Emission) {
        args.forEach { emission.push(it) }
        emission.invokeMath(name, args.map { it.type }, type)
    }
}

// sqrt/sin/cos/... : Double -> Double only; pow: (Double, Double) -> Double.
private class DoubleFunction(
    override val name: String,
    arity: Int,
    private val op: (List<Double>) -> Double,
) : FunctionParser {
    override val minArgs = arity
    override val maxArgs = arity

    override fun compile(args: List<TypedNode>): TypedNode {
        args.forEach { arg ->
            if (arg.type != Double::class) {
                throw ExpressionException("function '$name' expects Double arguments, got ${arg.type.simpleName}")
            }
        }
        return MathFunctionNode(name, args, Double::class) { values -> op(values.map { it as Double }) }
    }
}

// abs/min/max: all arguments must share one numeric type, result keeps it.
private class HomogeneousNumericFunction(
    override val name: String,
    arity: Int,
    private val implFor: (KClass<*>) -> (List<Any?>) -> Any?,
) : FunctionParser {
    override val minArgs = arity
    override val maxArgs = arity

    override fun compile(args: List<TypedNode>): TypedNode {
        val type = args.first().type
        if (type !in NUMERIC_TYPES || args.any { it.type != type }) {
            throw ExpressionException(
                "function '$name' expects all arguments to have the same numeric type," +
                    " got ${args.joinToString(", ") { it.type.simpleName ?: "?" }}"
            )
        }
        return MathFunctionNode(name, args, type, implFor(type))
    }
}

private fun absImpl(type: KClass<*>): (List<Any?>) -> Any? = when (type) {
    Int::class -> { v -> abs(v[0] as Int) }
    Long::class -> { v -> abs(v[0] as Long) }
    Float::class -> { v -> abs(v[0] as Float) }
    Double::class -> { v -> abs(v[0] as Double) }
    else -> error("unreachable: type checked by HomogeneousNumericFunction")
}

private fun minImpl(type: KClass<*>): (List<Any?>) -> Any? = when (type) {
    Int::class -> { v -> minOf(v[0] as Int, v[1] as Int) }
    Long::class -> { v -> minOf(v[0] as Long, v[1] as Long) }
    Float::class -> { v -> minOf(v[0] as Float, v[1] as Float) }
    Double::class -> { v -> minOf(v[0] as Double, v[1] as Double) }
    else -> error("unreachable: type checked by HomogeneousNumericFunction")
}

private fun maxImpl(type: KClass<*>): (List<Any?>) -> Any? = when (type) {
    Int::class -> { v -> maxOf(v[0] as Int, v[1] as Int) }
    Long::class -> { v -> maxOf(v[0] as Long, v[1] as Long) }
    Float::class -> { v -> maxOf(v[0] as Float, v[1] as Float) }
    Double::class -> { v -> maxOf(v[0] as Double, v[1] as Double) }
    else -> error("unreachable: type checked by HomogeneousNumericFunction")
}

private class ConversionFunction(
    override val name: String,
    private val target: KClass<*>,
) : FunctionParser {
    override val minArgs = 1
    override val maxArgs = 1

    override fun compile(args: List<TypedNode>): TypedNode {
        val arg = args.single()
        if (arg.type !in NUMERIC_TYPES) {
            throw ExpressionException("function '$name' expects a numeric argument, got ${arg.type.simpleName}")
        }
        return ConvertNumericNode(target, arg)
    }
}

object MathFunctionsExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        registry.registerTokenParser(
            RegexpTokenParser(IdentifierTokenType, Regex("[a-zA-Z_][a-zA-Z0-9_]*"), LOW_TOKEN_PRIORITY)
        )
        registry.registerFunctionNameTokenType(IdentifierTokenType)

        registry.registerFunction(DoubleFunction("sqrt", 1) { sqrt(it[0]) })
        registry.registerFunction(DoubleFunction("sin", 1) { sin(it[0]) })
        registry.registerFunction(DoubleFunction("cos", 1) { cos(it[0]) })
        registry.registerFunction(DoubleFunction("tan", 1) { tan(it[0]) })
        registry.registerFunction(DoubleFunction("ln", 1) { ln(it[0]) })
        registry.registerFunction(DoubleFunction("log10", 1) { log10(it[0]) })
        registry.registerFunction(DoubleFunction("exp", 1) { exp(it[0]) })
        registry.registerFunction(DoubleFunction("floor", 1) { floor(it[0]) })
        registry.registerFunction(DoubleFunction("ceil", 1) { ceil(it[0]) })
        registry.registerFunction(DoubleFunction("round", 1) { round(it[0]) })
        registry.registerFunction(DoubleFunction("pow", 2) { it[0].pow(it[1]) })

        registry.registerFunction(HomogeneousNumericFunction("abs", 1, ::absImpl))
        registry.registerFunction(HomogeneousNumericFunction("min", 2, ::minImpl))
        registry.registerFunction(HomogeneousNumericFunction("max", 2, ::maxImpl))

        registry.registerFunction(ConversionFunction("toInt", Int::class))
        registry.registerFunction(ConversionFunction("toLong", Long::class))
        registry.registerFunction(ConversionFunction("toFloat", Float::class))
        registry.registerFunction(ConversionFunction("toDouble", Double::class))
    }
}
