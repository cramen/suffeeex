package ru.cramen.suffeeex.core.lexical.v2.tokens

import kotlin.math.min

object StringTokenType: TokenType()

object StringTokenParser: TokenParser() {
    override val priority: Int
        get() = HIGH_TOKEN_PRIORITY

    override fun match(exp: String, position: Int): Token? {
        if (exp.substring(position, min(position + 1, exp.length) ) != "\"") return null

        var offset = position + 1
        while(exp.substring(position + offset, position + offset + 1) != "\"" || exp[position + offset - 1] == '\\') {
            offset++
        }
        val string = exp.substring(position, position + offset + 1)
            .replace("\\\"", "\"")
        return Token(StringTokenType, string, position)
    }

}
