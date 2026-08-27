package ru.cramen.suffeeex.ext.math.function

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.DifferentiableNode
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.NUMERIC_TYPES
import ru.cramen.suffeeex.core.node.NumericOp
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.node.differentiateOrThrow
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.FunctionParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.LOW_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.core.token.TokenType
import ru.cramen.suffeeex.ext.math.number.NumberLiteralNode
import ru.cramen.suffeeex.ext.math.operator.BinaryArithmeticNode
import ru.cramen.suffeeex.ext.math.operator.NegationNode
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
) : TypedNode, DifferentiableNode {
    override fun build(): Expression {
        val built = args.map { it.build() }
        return Expression { c -> impl(built.map { arg -> arg.eval(c) }) }
    }

    override fun emit(emission: Emission) {
        args.forEach { emission.push(it) }
        emission.invokeMath(name, args.map { it.type }, type)
    }

    override fun differentiate(by: String): TypedNode {
        if (type != Double::class) {
            throw ExpressionException(
                "function '$name' result is ${type.simpleName}: differentiation is supported for Double expressions only"
            )
        }
        val f = args[0]
        val df = f.differentiateOrThrow(by)
        return when (name) {
            "sqrt" -> div(df, mul(doubleConst(2.0), doubleFunctionNode("sqrt", listOf(f))))
            "sin" -> mul(doubleFunctionNode("cos", listOf(f)), df)
            "cos" -> mul(NegationNode(doubleFunctionNode("sin", listOf(f))), df)
            "tan" -> {
                val cos = doubleFunctionNode("cos", listOf(f))
                div(df, mul(cos, cos))
            }
            "ln" -> div(df, f)
            "log10" -> div(df, mul(f, doubleConst(ln(10.0))))
            "exp" -> mul(doubleFunctionNode("exp", listOf(f)), df)
            "pow" -> {
                val g = args[1]
                val dg = g.differentiateOrThrow(by)
                mul(
                    doubleFunctionNode("pow", listOf(f, g)),
                    add(mul(dg, doubleFunctionNode("ln", listOf(f))), div(mul(g, df), f)),
                )
            }
            else -> throw ExpressionException("function '$name' is not differentiable")
        }
    }
}

private fun add(left: TypedNode, right: TypedNode) = BinaryArithmeticNode(NumericOp.ADD, left, right)
private fun mul(left: TypedNode, right: TypedNode) = BinaryArithmeticNode(NumericOp.MUL, left, right)
private fun div(left: TypedNode, right: TypedNode) = BinaryArithmeticNode(NumericOp.DIV, left, right)
private fun doubleConst(value: Double) = NumberLiteralNode(value, Double::class)

// Implementations of the Double-only math functions, shared between
// DoubleFunction and the derivative rules in MathFunctionNode.differentiate.
private val DOUBLE_FUNCTION_OPS: Map<String, (List<Double>) -> Double> = mapOf(
    "sqrt" to { sqrt(it[0]) },
    "sin" to { sin(it[0]) },
    "cos" to { cos(it[0]) },
    "tan" to { tan(it[0]) },
    "ln" to { ln(it[0]) },
    "log10" to { log10(it[0]) },
    "exp" to { exp(it[0]) },
    "floor" to { floor(it[0]) },
    "ceil" to { ceil(it[0]) },
    "round" to { round(it[0]) },
    "pow" to { it[0].pow(it[1]) },
)

/** Builds a node for a registered Double math function, reusing its shared implementation. */
internal fun doubleFunctionNode(name: String, args: List<TypedNode>): MathFunctionNode {
    val op = DOUBLE_FUNCTION_OPS.getValue(name)
    return MathFunctionNode(name, args, Double::class) { values -> op(values.map { it as Double }) }
}

// sqrt/sin/cos/... : Double -> Double only; pow: (Double, Double) -> Double.
private class DoubleFunction(
    override val name: String,
    arity: Int,
) : FunctionParser {
    override val minArgs = arity
    override val maxArgs = arity

    override fun compile(args: List<TypedNode>): TypedNode {
        args.forEach { arg ->
            if (arg.type != Double::class) {
                throw ExpressionException("function '$name' expects Double arguments, got ${arg.type.simpleName}")
            }
        }
        return doubleFunctionNode(name, args)
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

        registry.registerFunction(DoubleFunction("sqrt", 1))
        registry.registerFunction(DoubleFunction("sin", 1))
        registry.registerFunction(DoubleFunction("cos", 1))
        registry.registerFunction(DoubleFunction("tan", 1))
        registry.registerFunction(DoubleFunction("ln", 1))
        registry.registerFunction(DoubleFunction("log10", 1))
        registry.registerFunction(DoubleFunction("exp", 1))
        registry.registerFunction(DoubleFunction("floor", 1))
        registry.registerFunction(DoubleFunction("ceil", 1))
        registry.registerFunction(DoubleFunction("round", 1))
        registry.registerFunction(DoubleFunction("pow", 2))

        registry.registerFunction(HomogeneousNumericFunction("abs", 1, ::absImpl))
        registry.registerFunction(HomogeneousNumericFunction("min", 2, ::minImpl))
        registry.registerFunction(HomogeneousNumericFunction("max", 2, ::maxImpl))

        registry.registerFunction(ConversionFunction("toInt", Int::class))
        registry.registerFunction(ConversionFunction("toLong", Long::class))
        registry.registerFunction(ConversionFunction("toFloat", Float::class))
        registry.registerFunction(ConversionFunction("toDouble", Double::class))
    }
}
