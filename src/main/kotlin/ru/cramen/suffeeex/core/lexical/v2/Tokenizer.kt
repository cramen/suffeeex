package ru.cramen.suffeeex.core.lexical.v2

import ru.cramen.suffeeex.core.lexical.v2.tokens.Token
import ru.cramen.suffeeex.core.lexical.v2.tokens.TokenParser

class Tokenizer(tokenParsers: Collection<TokenParser>) {
    private val byPriority = tokenParsers.groupBy { it.priority }
    private val priorities = byPriority.keys.sortedDescending()

    fun tokenize(str: String): List<Token> {
        var position = 0
        val result = mutableListOf<Token>()
        while (position < str.length) {
            val tokens = byPriority.asSequence().mapNotNull { (_, tokenParsers) ->
                tokenParsers.mapNotNull { it.match(str, position) }
            }.firstOrNull()

            if (tokens.isNullOrEmpty()) {
                throw TokenizerException("unknown token on position $position")
            }
            val token = tokens.maxBy { it.value.length }
            position += token.value.length
            result.add(token)
        }
        return result
    }
}
