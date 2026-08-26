package ru.cramen.suffeeex.ext.logic

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.CompareOp
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.NUMERIC_TYPES
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.FunctionParser
import ru.cramen.suffeeex.core.syntax.InfixOperatorParser
import ru.cramen.suffeeex.core.syntax.LiteralParser
import ru.cramen.suffeeex.core.syntax.PrefixOperatorParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.HIGH_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.LOW_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.MEDIUM_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.core.token.SimpleMultiTokenParser
import ru.cramen.suffeeex.core.token.SimpleTokenParser
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import ru.cramen.suffeeex.ext.math.function.IdentifierTokenType
import kotlin.reflect.KClass

object TrueTokenType : TokenType()
object FalseTokenType : TokenType()
object LessTokenType : TokenType()
object LessOrEqualTokenType : TokenType()
object GreaterTokenType : TokenType()
object GreaterOrEqualTokenType : TokenType()
object EqualsTokenType : TokenType()
object NotEqualsTokenType : TokenType()
object AndTokenType : TokenType()
object OrTokenType : TokenType()
object NotTokenType : TokenType()

private val COMPARISON_SYMBOLS = mapOf(
    CompareOp.LT to "<",
    CompareOp.LE to "<=",
    CompareOp.GT to ">",
    CompareOp.GE to ">=",
)

/** Boolean literal `true` / `false`. */
class BooleanLiteralNode(val value: Boolean) : TypedNode {
    override val type: KClass<*> = Boolean::class

    override fun build(): Expression = Expression { value }

    override fun emit(emission: Emission) = emission.constant(Boolean::class, value)
}

/** Logical negation of a Boolean operand. */
class LogicalNotNode(val operand: TypedNode) : TypedNode {
    override val type: KClass<*> = Boolean::class

    init {
        if (operand.type != Boolean::class) {
            throw ExpressionException("operator '!' requires a Boolean operand, got ${operand.type.simpleName}")
        }
    }

    override fun build(): Expression {
        val e = operand.build()
        return Expression { c -> !(e.eval(c) as Boolean) }
    }

    override fun emit(emission: Emission) {
        emission.push(operand)
        emission.logicalNot()
    }
}

/**
 * Short-circuit `&&` / `||` over Boolean operands: the right operand is
 * evaluated only when the left one does not decide the result.
 */
class LogicalOperatorNode(val and: Boolean, val left: TypedNode, val right: TypedNode) : TypedNode {
    override val type: KClass<*> = Boolean::class

    init {
        if (left.type != Boolean::class || right.type != Boolean::class) {
            throw ExpressionException(
                "operator '${if (and) "&&" else "||"}' requires both operands to be Boolean," +
                    " got ${left.type.simpleName} and ${right.type.simpleName}"
            )
        }
    }

    override fun build(): Expression {
        val l = left.build()
        val r = right.build()
        return if (and) {
            Expression { c -> (l.eval(c) as Boolean) && (r.eval(c) as Boolean) }
        } else {
            Expression { c -> (l.eval(c) as Boolean) || (r.eval(c) as Boolean) }
        }
    }

    override fun emit(emission: Emission) {
        if (and) emission.logicalAnd(left, right) else emission.logicalOr(left, right)
    }
}

/**
 * Same-type numeric comparison (`<`, `<=`, `>`, `>=`), result is Boolean.
 * Type dispatch happens once at compile time.
 */
class ComparisonNode(val op: CompareOp, val left: TypedNode, val right: TypedNode) : TypedNode {
    override val type: KClass<*> = Boolean::class

    private val operandType: KClass<*>

    init {
        if (left.type != right.type || left.type !in NUMERIC_TYPES) {
            throw ExpressionException(
                "operator '${COMPARISON_SYMBOLS.getValue(op)}' requires both operands to have the same numeric type," +
                    " got ${left.type.simpleName} and ${right.type.simpleName}"
            )
        }
        operandType = left.type
    }

    override fun build(): Expression {
        val l = left.build()
        val r = right.build()
        return when (operandType) {
            Int::class -> {
                val f = comparator<Int>(op)
                Expression { c -> f(l.eval(c) as Int, r.eval(c) as Int) }
            }
            Long::class -> {
                val f = comparator<Long>(op)
                Expression { c -> f(l.eval(c) as Long, r.eval(c) as Long) }
            }
            Float::class -> {
                val f = comparator<Float>(op)
                Expression { c -> f(l.eval(c) as Float, r.eval(c) as Float) }
            }
            Double::class -> {
                val f = comparator<Double>(op)
                Expression { c -> f(l.eval(c) as Double, r.eval(c) as Double) }
            }
            else -> error("unreachable: type checked in init")
        }
    }

    override fun emit(emission: Emission) {
        emission.push(left)
        emission.push(right)
        emission.compare(op, operandType)
    }
}

/**
 * Equality (`==` / `!=`) of same-type operands: numeric types and Boolean
 * compare by value, String via structural equality. Result is Boolean.
 */
class EqualityNode(val op: CompareOp, val left: TypedNode, val right: TypedNode) : TypedNode {
    override val type: KClass<*> = Boolean::class

    private val operandType: KClass<*>

    init {
        if (left.type != right.type ||
            (left.type !in NUMERIC_TYPES && left.type != String::class && left.type != Boolean::class)
        ) {
            throw ExpressionException(
                "operator '${if (op == CompareOp.EQ) "==" else "!="}' requires both operands to have" +
                    " the same numeric, Boolean or String type, got ${left.type.simpleName} and ${right.type.simpleName}"
            )
        }
        operandType = left.type
    }

    override fun build(): Expression {
        if (operandType in NUMERIC_TYPES) return ComparisonNode(op, left, right).build()
        // String and Boolean: Kotlin '==' is null-safe structural equality
        val l = left.build()
        val r = right.build()
        return Expression { c -> (l.eval(c) == r.eval(c)) == (op == CompareOp.EQ) }
    }

    override fun emit(emission: Emission) {
        emission.push(left)
        emission.push(right)
        if (operandType == String::class) {
            emission.objectsEquals()
            if (op == CompareOp.NE) emission.logicalNot()
        } else {
            emission.compare(op, operandType)
        }
    }
}

private fun <T : Comparable<T>> comparator(op: CompareOp): (T, T) -> Boolean = when (op) {
    CompareOp.LT -> { a, b -> a < b }
    CompareOp.LE -> { a, b -> a <= b }
    CompareOp.GT -> { a, b -> a > b }
    CompareOp.GE -> { a, b -> a >= b }
    CompareOp.EQ -> { a, b -> a == b }
    CompareOp.NE -> { a, b -> a != b }
}

/**
 * `if(condition, ifTrue, ifFalse)`: the condition must be Boolean and both
 * branches must share a type, which becomes the node type. Lazy: only the
 * chosen branch is evaluated.
 */
class IfNode(val condition: TypedNode, val ifTrue: TypedNode, val ifFalse: TypedNode) : TypedNode {
    override val type: KClass<*>

    init {
        if (condition.type != Boolean::class) {
            throw ExpressionException("function 'if' expects a Boolean condition, got ${condition.type.simpleName}")
        }
        if (ifTrue.type != ifFalse.type) {
            throw ExpressionException(
                "function 'if' expects both branches to have the same type," +
                    " got ${ifTrue.type.simpleName} and ${ifFalse.type.simpleName}"
            )
        }
        type = ifTrue.type
    }

    override fun build(): Expression {
        val cond = condition.build()
        val t = ifTrue.build()
        val f = ifFalse.build()
        return Expression { c -> if (cond.eval(c) as Boolean) t.eval(c) else f.eval(c) }
    }

    override fun emit(emission: Emission) {
        emission.branch(condition, ifTrue, ifFalse)
    }
}

private fun infix(tokenType: TokenType, precedence: Int, compile: (TypedNode, TypedNode) -> TypedNode) =
    object : InfixOperatorParser {
        override val tokenType = tokenType
        override val precedence = precedence
        override fun compile(left: TypedNode, right: TypedNode): TypedNode = compile(left, right)
    }

private fun booleanLiteral(tokenType: TokenType, value: Boolean) = object : LiteralParser {
    override val tokenType = tokenType
    override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode = BooleanLiteralNode(value)
}

object LogicExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        // HIGH priority: must beat the identifier regex (LOW)
        registry.registerTokenParser(SimpleTokenParser(TrueTokenType, "true", HIGH_TOKEN_PRIORITY))
        registry.registerTokenParser(SimpleTokenParser(FalseTokenType, "false", HIGH_TOKEN_PRIORITY))

        // longest match is handled internally ("!=" before "!", "<=" before "<")
        registry.registerTokenParser(
            SimpleMultiTokenParser(
                mapOf(
                    "<=" to LessOrEqualTokenType,
                    ">=" to GreaterOrEqualTokenType,
                    "==" to EqualsTokenType,
                    "!=" to NotEqualsTokenType,
                    "&&" to AndTokenType,
                    "||" to OrTokenType,
                    "<" to LessTokenType,
                    ">" to GreaterTokenType,
                    "!" to NotTokenType,
                ),
                priority = MEDIUM_TOKEN_PRIORITY,
            )
        )

        registry.registerLiteral(booleanLiteral(TrueTokenType, true))
        registry.registerLiteral(booleanLiteral(FalseTokenType, false))

        registry.registerPrefixOperator(object : PrefixOperatorParser {
            override val tokenType = NotTokenType
            override val precedence = 30
            override fun compile(operand: TypedNode): TypedNode = LogicalNotNode(operand)
        })

        registry.registerInfixOperator(infix(LessTokenType, 7) { l, r -> ComparisonNode(CompareOp.LT, l, r) })
        registry.registerInfixOperator(infix(LessOrEqualTokenType, 7) { l, r -> ComparisonNode(CompareOp.LE, l, r) })
        registry.registerInfixOperator(infix(GreaterTokenType, 7) { l, r -> ComparisonNode(CompareOp.GT, l, r) })
        registry.registerInfixOperator(infix(GreaterOrEqualTokenType, 7) { l, r -> ComparisonNode(CompareOp.GE, l, r) })
        registry.registerInfixOperator(infix(EqualsTokenType, 6) { l, r -> EqualityNode(CompareOp.EQ, l, r) })
        registry.registerInfixOperator(infix(NotEqualsTokenType, 6) { l, r -> EqualityNode(CompareOp.NE, l, r) })
        registry.registerInfixOperator(infix(AndTokenType, 5) { l, r -> LogicalOperatorNode(true, l, r) })
        registry.registerInfixOperator(infix(OrTokenType, 4) { l, r -> LogicalOperatorNode(false, l, r) })

        // `if` is a function; the identifier token type is shared with
        // MathFunctionsExtension so both extensions compose (registering an
        // identical parser for the same token type is harmless)
        registry.registerTokenParser(
            RegexpTokenParser(IdentifierTokenType, Regex("[a-zA-Z_][a-zA-Z0-9_]*"), LOW_TOKEN_PRIORITY)
        )
        registry.registerFunctionNameTokenType(IdentifierTokenType)
        registry.registerFunction(object : FunctionParser {
            override val name = "if"
            override val minArgs = 3
            override val maxArgs = 3
            override fun compile(args: List<TypedNode>): TypedNode = IfNode(args[0], args[1], args[2])
        })
    }
}
