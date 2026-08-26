package ru.cramen.suffeeex.core.token

import ru.cramen.suffeeex.core.ExpressionException

private val SKIPPED_CHARS = charArrayOf(' ', '\t', '\r', '\n')

class Tokenizer(tokenParsers: Collection<TokenParser>) {
    private val byPriority = tokenParsers.groupBy { it.priority }
    private val priorities = byPriority.keys.sortedDescending()

    fun tokenize(str: String): List<Token> {
        var position = 0
        val result = mutableListOf<Token>()
        while (position < str.length) {
            if (str[position] in SKIPPED_CHARS) {
                position++
                continue
            }
            val tokens = priorities.asSequence()
                .mapNotNull { byPriority[it] }
                .mapNotNull { tokenParsers ->
                    tokenParsers.mapNotNull { it.match(str, position) }.takeIf { it.isNotEmpty() }
                }
                .firstOrNull()

            if (tokens.isNullOrEmpty()) {
                throw ExpressionException("unknown token at position $position")
            }
            val token = tokens.maxBy { it.value.length }
            position += token.value.length
            result.add(token)
        }
        return result
    }
}
