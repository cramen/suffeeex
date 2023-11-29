package ru.cramen.suffeeex.core.lexical.v2.tokens.parsers.common

import ru.cramen.suffeeex.core.lexical.v2.tokens.MEDIUM_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.lexical.v2.tokens.SimpleTokenParser
import ru.cramen.suffeeex.core.lexical.v2.tokens.TokenType

object OperatorTokenType : TokenType()

abstract class OperatorTokenParser(
    final override val tokenString: String
) : SimpleTokenParser(OperatorTokenType, tokenString, MEDIUM_TOKEN_PRIORITY) {
    init {
        if (!tokenString.matches(checkRegexp))
            throw IllegalArgumentException("$tokenString is not matches to operator regex")
    }

    companion object {
        private val checkRegexp = "^[a-zA-Z][_a-zA-Z0-9]*$".toRegex()
    }
}