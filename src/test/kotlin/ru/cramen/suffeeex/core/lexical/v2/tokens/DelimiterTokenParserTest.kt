package ru.cramen.suffeeex.core.lexical.v2.tokens

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.cramen.suffeeex.core.lexical.v2.Tokenizer


internal class DelimiterTokenParserTest {

    @Test
    fun match() {
        val tokenizer = Tokenizer(listOf(DelimiterTokenParser))
        val tokens = tokenizer.tokenize(" \t\r\n,")
        tokens shouldHaveSize 5
        tokens shouldBe listOf(
            Token(SpaceTokenType, " ", 0),
            Token(SpaceTokenType, "\t", 1),
            Token(SpaceTokenType, "\r", 2),
            Token(SpaceTokenType, "\n", 3),
            Token(CommaTokenType, ",", 4),
        )
    }
}
