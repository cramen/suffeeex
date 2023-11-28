package ru.cramen.suffeeex.core.lexical.v2.tokens

import kotlin.math.min

abstract class TokenType

data class Token(
    val type: TokenType,
    val value: String,
    val position: Int
)

abstract class TokenParser {
    abstract val priority: Int
    abstract fun match(exp: String, position: Int): Token?
}

open class SimpleTokenParser(
    val type: TokenType,
    val tokenString: String,
    override val priority: Int = LOW_TOKEN_PRIORITY,
): TokenParser() {
    override fun match(exp: String, position: Int): Token? {
        return try {
            if (exp.substring(position, position + tokenString.length) == tokenString) {
                Token(type, tokenString, position)
            } else {
                null
            }
        } catch (e: StringIndexOutOfBoundsException) {
            null
        }
    }
}

open class SimpleMultiTokenParser(
    tokenMap: Map<String, TokenType>,
    override val priority: Int = LOW_TOKEN_PRIORITY,
) : TokenParser() {
    private val byLength = tokenMap.entries
        .groupBy { it.key.length }
        .map {
            it.key to it.value.map { it.key to it.value }.toMap()
        }
        .toMap()
    private val sortedKeysDesc = byLength.keys.sortedDescending()

    override fun match(exp: String, position: Int): Token? {
        return sortedKeysDesc.asSequence().mapNotNull { tokenLength ->
            val parsers = byLength[tokenLength] ?: return@mapNotNull null
            val firstSymbols = exp.substring(position, min(position + tokenLength, exp.length))
            parsers[firstSymbols]?.let { Token(it, firstSymbols, position) }
        }.firstOrNull()
    }
}

open class RegexpTokenParser(
    val type: TokenType,
    val regExp: Regex,
    override val priority: Int = LOW_TOKEN_PRIORITY,
): TokenParser() {
    override fun match(exp: String, position: Int): Token? {
        return try {
            val expOnPosition = exp.substring(position, exp.length)
            val matchResult = regExp.matchAt(expOnPosition, 0)
            matchResult?.groups?.get(0)?.value?.let { Token(type, it, position) }
        } catch (e: StringIndexOutOfBoundsException) {
            null
        }
    }
}
