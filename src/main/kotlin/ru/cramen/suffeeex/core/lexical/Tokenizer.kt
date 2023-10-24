package ru.cramen.suffeeex.core.lexical

import ru.cramen.suffeeex.core.lexical.tokens.*


class Tokenizer {

    fun tokenize(stringExpression: String): List<Token> {
        var index = 0
        val result = mutableListOf<Token>()
        while (index < stringExpression.length) {
            val token =
                getMatchedToken(stringExpression, index) ?: throw TokenizerException("unknown token on position $index")
            index += token.length
            result.add(token)
        }
        return result
    }


    private fun getMatchedToken(s: String, position: Int = 0): Token? {
        if (position >= s.length) return null
        return when (s.substring(position, position + 1)) {
            "(" -> OpenBracketToken
            ")" -> CloseBracketToken
            "[" -> OpenSquareBracketToken
            "]" -> CloseSquareBracketToken
            "," -> CommaToken
            "\"" -> whileString(s, position)?.let { return StringToken(it) }
            "$" -> variableSymbols.whileStartsString(s, position)?.checkVariableFormat()?.let { return VariableToken(it) }
            in spaces -> spaces.whileStartsString(s, position)?.let { return SpaceToken(it) }
            in numbers -> numbers.whileStartsString(s, position)?.checkNumberFormat()?.let { return NumberToken(it) }
            !in notOperatorSymbols -> notOperatorSymbols.whileNotStartsString(s, position)?.checkOperatorFormat()?.let { return OperatorToken(it) }
            else -> null
        }
    }

    private fun String.checkNumberFormat() = this.takeIf { it.matches("^\\d+(\\.\\d+)?$".toRegex()) }
    private fun String.checkOperatorFormat() = this //TODO надо подумать, какая тут должна быть проверка
    private fun String.checkVariableFormat() = this.takeIf { it.matches("^\\\$[a-zA-Z][a-zA-Z\\d]*$".toRegex()) }


    private fun Set<String>.whileStartsString(s: String, position: Int = 0): String? {
        var index = position
        while (index < s.length && s.substring(index, index + 1) in this) {
            index++
        }
        if (index == position) return null
        return s.substring(position, index)
    }

    private fun Set<String>.whileNotStartsString(s: String, position: Int = 0): String? {
        var index = position
        while (index < s.length && s.substring(index, index + 1) !in this) {
            index++
        }
        if (index == position) return null
        return s.substring(position, index)
    }

    private fun whileString(s: String, position: Int = 0): String? {
        var index = position + 1
        while (index < s.length && s.substring(index, index + 1) != "\"" || s[index - 1] == '\\') {
            index++
        }
        if (s.substring(index, index + 1) != "\"") return null
        return s.substring(position, index + 1).replace("\\\"", "\"")
    }

    companion object {
        private fun CharRange.toSet() = this.map { it.toString() }.toSet()
        private val special = setOf("(", ")", "[", "]", ",", "$", "\"")
        private val spaces = setOf(" ", "\r", "\n", "\t")
        private val numbers = ('0'..'9').toSet() + "."
        private val variableSymbols = ('a'..'z').toSet() +
                ('A'..'Z').toSet() +
                ('0'..'9').toSet() + "$"
        private val notOperatorSymbols = setOf("$", ) + special + spaces
    }
}
