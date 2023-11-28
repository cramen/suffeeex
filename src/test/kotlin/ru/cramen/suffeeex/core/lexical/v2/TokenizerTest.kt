package ru.cramen.suffeeex.core.lexical.v2

import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.core.lexical.v2.tokens.SimpleTokenParser
import ru.cramen.suffeeex.core.lexical.v2.tokens.Token
import ru.cramen.suffeeex.core.lexical.v2.tokens.TokenType
import kotlin.test.Test

internal class TokenizerTest {

    private val tokenType = object : TokenType() {}

    @Test
    fun `test prefer high priority`() {
        val tp1 = SimpleTokenParser(tokenType, "foo", 1)
        val tp2 = SimpleTokenParser(tokenType, "bar", 1)
        val tp3 = SimpleTokenParser(tokenType, "foobar", 0)
        val tokenizer = Tokenizer(listOf(tp1, tp2, tp3))

        val expected = listOf(
            Token(tokenType, "foo", 0),
            Token(tokenType, "bar", 3),
        )
        tokenizer.tokenize("foobar") shouldBe expected
    }

    @Test
    fun `test return longer token`() {
        val tp1 = SimpleTokenParser(tokenType, "a")
        val tp2 = SimpleTokenParser(tokenType, "ab")
        val tp3 = SimpleTokenParser(tokenType, "abc")
        val tokenizer = Tokenizer(listOf(tp1, tp2, tp3))

        val expected = listOf(Token(tokenType, "abc", 0))
        tokenizer.tokenize("abc") shouldBe expected
    }
}
