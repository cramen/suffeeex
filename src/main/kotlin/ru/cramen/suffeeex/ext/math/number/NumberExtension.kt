package ru.cramen.suffeeex.ext.math.number

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
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

class NumberLiteralNode(val value: Number, override val type: KClass<*>) : TypedNode {
    override fun build(): Expression = Expression { value }

    override fun emit(emission: Emission) = emission.constant(type, value)
}

object NumberExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        registry.registerTokenParser(
            RegexpTokenParser(NumberTokenType, Regex("\\d+(\\.\\d+)?[fFlL]?"), MEDIUM_TOKEN_PRIORITY)
        )
        registry.registerLiteral(object : LiteralParser {
            override val tokenType = NumberTokenType

            // Kotlin-style literal typing: 123 -> Int (Long on Int overflow),
            // 123L -> Long, 1.5 -> Double, 1.5f / 123f -> Float;
            // a '.' combined with the L/l suffix is a compile error.
            override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode {
                val text = token.value
                val suffix = text.last().lowercaseChar().takeIf { it == 'f' || it == 'l' }
                val digits = if (suffix != null) text.dropLast(1) else text
                val hasDot = '.' in text
                return when {
                    hasDot && suffix == 'l' ->
                        throw ExpressionException("invalid number literal '$text': 'L' suffix is not allowed on floating-point literals")
                    suffix == 'f' -> NumberLiteralNode(digits.toFloat(), Float::class)
                    hasDot -> NumberLiteralNode(text.toDouble(), Double::class)
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
