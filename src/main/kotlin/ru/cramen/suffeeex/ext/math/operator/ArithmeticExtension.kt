package ru.cramen.suffeeex.ext.math.operator

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.DifferentiableNode
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.NUMERIC_TYPES
import ru.cramen.suffeeex.core.node.NumericOp
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.node.differentiateOrThrow
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.InfixOperatorParser
import ru.cramen.suffeeex.core.syntax.PrefixOperatorParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.MEDIUM_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.SimpleMultiTokenParser
import ru.cramen.suffeeex.core.token.TokenType
import kotlin.reflect.KClass

object PlusTokenType : TokenType()
object MinusTokenType : TokenType()
object MultiplyTokenType : TokenType()
object DivideTokenType : TokenType()
object PercentTokenType : TokenType()

private val OP_SYMBOLS = mapOf(
    NumericOp.ADD to "+",
    NumericOp.SUB to "-",
    NumericOp.MUL to "*",
    NumericOp.DIV to "/",
    NumericOp.REM to "%",
)

private val INT_OPS: Map<NumericOp, (Int, Int) -> Int> = mapOf(
    NumericOp.ADD to Int::plus,
    NumericOp.SUB to Int::minus,
    NumericOp.MUL to Int::times,
    NumericOp.DIV to Int::div,
    NumericOp.REM to Int::rem,
)

private val LONG_OPS: Map<NumericOp, (Long, Long) -> Long> = mapOf(
    NumericOp.ADD to Long::plus,
    NumericOp.SUB to Long::minus,
    NumericOp.MUL to Long::times,
    NumericOp.DIV to Long::div,
    NumericOp.REM to Long::rem,
)

private val FLOAT_OPS: Map<NumericOp, (Float, Float) -> Float> = mapOf(
    NumericOp.ADD to Float::plus,
    NumericOp.SUB to Float::minus,
    NumericOp.MUL to Float::times,
    NumericOp.DIV to Float::div,
    NumericOp.REM to Float::rem,
)

private val DOUBLE_OPS: Map<NumericOp, (Double, Double) -> Double> = mapOf(
    NumericOp.ADD to Double::plus,
    NumericOp.SUB to Double::minus,
    NumericOp.MUL to Double::times,
    NumericOp.DIV to Double::div,
    NumericOp.REM to Double::rem,
)

/**
 * Same-type numeric binary operation: no coercion, the operand types must be
 * equal and numeric, and the result keeps that type. Type dispatch happens
 * once at compile time.
 */
class BinaryArithmeticNode(val op: NumericOp, val left: TypedNode, val right: TypedNode) : TypedNode, DifferentiableNode {
    override val type: KClass<*>

    init {
        if (left.type != right.type || left.type !in NUMERIC_TYPES) {
            throw ExpressionException(
                "operator '${OP_SYMBOLS.getValue(op)}' requires both operands to have the same numeric type," +
                    " got ${left.type.simpleName} and ${right.type.simpleName}"
            )
        }
        type = left.type
    }

    override fun build(): Expression {
        val l = left.build()
        val r = right.build()
        return when (type) {
            Int::class -> {
                val f = INT_OPS.getValue(op)
                Expression { c -> f(l.eval(c) as Int, r.eval(c) as Int) }
            }
            Long::class -> {
                val f = LONG_OPS.getValue(op)
                Expression { c -> f(l.eval(c) as Long, r.eval(c) as Long) }
            }
            Float::class -> {
                val f = FLOAT_OPS.getValue(op)
                Expression { c -> f(l.eval(c) as Float, r.eval(c) as Float) }
            }
            Double::class -> {
                val f = DOUBLE_OPS.getValue(op)
                Expression { c -> f(l.eval(c) as Double, r.eval(c) as Double) }
            }
            else -> error("unreachable: type checked in init")
        }
    }

    override fun emit(emission: Emission) {
        emission.push(left)
        emission.push(right)
        emission.numericBinary(op, type)
    }

    override fun differentiate(by: String): TypedNode {
        if (type != Double::class) {
            throw ExpressionException(
                "differentiation is only supported for Double expressions, got ${type.simpleName}"
            )
        }
        val dl = left.differentiateOrThrow(by)
        val dr = right.differentiateOrThrow(by)
        return when (op) {
            NumericOp.ADD -> BinaryArithmeticNode(NumericOp.ADD, dl, dr)
            NumericOp.SUB -> BinaryArithmeticNode(NumericOp.SUB, dl, dr)
            NumericOp.MUL -> BinaryArithmeticNode(
                NumericOp.ADD,
                BinaryArithmeticNode(NumericOp.MUL, dl, right),
                BinaryArithmeticNode(NumericOp.MUL, left, dr),
            )
            NumericOp.DIV -> BinaryArithmeticNode(
                NumericOp.DIV,
                BinaryArithmeticNode(
                    NumericOp.SUB,
                    BinaryArithmeticNode(NumericOp.MUL, dl, right),
                    BinaryArithmeticNode(NumericOp.MUL, left, dr),
                ),
                BinaryArithmeticNode(NumericOp.MUL, right, right),
            )
            NumericOp.REM -> throw ExpressionException("differentiation of '%' is not supported")
        }
    }
}

/** Unary minus on a numeric operand; the result keeps the operand type. */
class NegationNode(val operand: TypedNode) : TypedNode, DifferentiableNode {
    override val type: KClass<*> = operand.type

    init {
        if (type !in NUMERIC_TYPES) {
            throw ExpressionException("unary '-' requires a numeric operand, got ${type.simpleName}")
        }
    }

    override fun build(): Expression {
        val e = operand.build()
        return when (type) {
            Int::class -> Expression { c -> -(e.eval(c) as Int) }
            Long::class -> Expression { c -> -(e.eval(c) as Long) }
            Float::class -> Expression { c -> -(e.eval(c) as Float) }
            Double::class -> Expression { c -> -(e.eval(c) as Double) }
            else -> error("unreachable: type checked in init")
        }
    }

    override fun emit(emission: Emission) {
        emission.push(operand)
        emission.numericNegate(type)
    }

    override fun differentiate(by: String): TypedNode {
        if (type != Double::class) {
            throw ExpressionException(
                "differentiation is only supported for Double expressions, got ${type.simpleName}"
            )
        }
        return NegationNode(operand.differentiateOrThrow(by))
    }
}

private fun infix(tokenType: TokenType, precedence: Int, op: NumericOp) = object : InfixOperatorParser {
    override val tokenType = tokenType
    override val precedence = precedence
    override fun compile(left: TypedNode, right: TypedNode): TypedNode = BinaryArithmeticNode(op, left, right)
}

object ArithmeticExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        registry.registerTokenParser(
            SimpleMultiTokenParser(
                mapOf(
                    "+" to PlusTokenType,
                    "-" to MinusTokenType,
                    "*" to MultiplyTokenType,
                    "/" to DivideTokenType,
                    "%" to PercentTokenType,
                ),
                priority = MEDIUM_TOKEN_PRIORITY,
            )
        )

        registry.registerInfixOperator(infix(PlusTokenType, 10, NumericOp.ADD))
        registry.registerInfixOperator(infix(MinusTokenType, 10, NumericOp.SUB))
        registry.registerInfixOperator(infix(MultiplyTokenType, 20, NumericOp.MUL))
        registry.registerInfixOperator(infix(DivideTokenType, 20, NumericOp.DIV))
        registry.registerInfixOperator(infix(PercentTokenType, 20, NumericOp.REM))

        registry.registerPrefixOperator(object : PrefixOperatorParser {
            override val tokenType = MinusTokenType
            override val precedence = 30
            override fun compile(operand: TypedNode): TypedNode = NegationNode(operand)
        })
    }
}
