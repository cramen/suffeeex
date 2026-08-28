package ru.cramen.suffeeex.ext.math.number

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.DifferentiableNode
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.LiteralParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.MEDIUM_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import kotlin.reflect.KClass

object NumberTokenType : TokenType()

class NumberLiteralNode(val value: Number, override val type: KClass<*>) : TypedNode, DifferentiableNode {
    override fun build(): Expression = Expression { value }

    override fun emit(emission: Emission) = emission.constant(type, value)

    // A derivative lives in the Double world regardless of the literal's own type;
    // the surrounding arithmetic rule enforces Double-only expressions.
    override fun differentiate(by: String): TypedNode = NumberLiteralNode(0.0, Double::class)
}

object NumberExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        registry.registerTokenParser(
            RegexpTokenParser(NumberTokenType, Regex("0[xX][0-9a-fA-F](_?[0-9a-fA-F])*[lL]?|0[bB][01](_?[01])*[lL]?|\\d(_?\\d)*(\\.\\d(_?\\d)*)?([eE][+-]?\\d(_?\\d)*)?[fFlL]?"), MEDIUM_TOKEN_PRIORITY)
        )
        registry.registerLiteral(object : LiteralParser {
            override val tokenType = NumberTokenType

            // Kotlin-style literal typing: 123 -> Int (Long on Int overflow),
            // 123L -> Long, 1.5 -> Double, 1.5f / 123f -> Float; underscores
            // between digits are ignored; 1e3 / 1.5e-3 -> Double (1e3f -> Float);
            // 0xFF / 0b101 -> Int with the same overflow-to-Long rule
            // (0xFFL / 0b101L -> Long); a '.' or exponent combined with the
            // L/l suffix is a compile error.
            override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode {
                val text = token.value
                val cleaned = text.replace("_", "")
                if (cleaned.startsWith("0x") || cleaned.startsWith("0X") ||
                    cleaned.startsWith("0b") || cleaned.startsWith("0B")
                ) {
                    val radix = if (cleaned[1] == 'x' || cleaned[1] == 'X') 16 else 2
                    // f/F is a hex digit, so only the l/L suffix is recognized here
                    val suffix = cleaned.last().lowercaseChar().takeIf { it == 'l' }
                    val digits = cleaned.substring(2).let { if (suffix != null) it.dropLast(1) else it }
                    val asLong = try {
                        digits.toLong(radix)
                    } catch (e: NumberFormatException) {
                        throw ExpressionException("integer literal out of range: '$text'")
                    }
                    return when {
                        suffix != null -> NumberLiteralNode(asLong, Long::class)
                        asLong in Int.MIN_VALUE..Int.MAX_VALUE -> NumberLiteralNode(asLong.toInt(), Int::class)
                        else -> NumberLiteralNode(asLong, Long::class)
                    }
                }
                val suffix = cleaned.last().lowercaseChar().takeIf { it == 'f' || it == 'l' }
                val digits = if (suffix != null) cleaned.dropLast(1) else cleaned
                val isFloating = '.' in digits || 'e' in digits || 'E' in digits
                return when {
                    isFloating && suffix == 'l' ->
                        throw ExpressionException("invalid number literal '$text': 'L' suffix is not allowed on floating-point literals")
                    suffix == 'f' -> NumberLiteralNode(digits.toFloat(), Float::class)
                    isFloating -> NumberLiteralNode(digits.toDouble(), Double::class)
                    suffix == 'l' -> NumberLiteralNode(digits.toLong(), Long::class)
                    else -> digits.toIntOrNull()?.let { NumberLiteralNode(it, Int::class) }
                        ?: NumberLiteralNode(
                            digits.toLongOrNull()
                                ?: throw ExpressionException("integer literal out of range: '$text'"),
                            Long::class,
                        )
                }
            }
        })
    }
}
