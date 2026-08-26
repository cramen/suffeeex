package ru.cramen.suffeeex.core.token

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.core.ExpressionException
import kotlin.test.Test

internal class TokenizerTest {

    private val tokenType = object : TokenType() {}

    @Test
    fun `test prefer high priority`() {
        val tp1 = SimpleTokenParser(tokenType, "foo", 1)
        val tp2 = SimpleTokenParser(tokenType, "bar", 1)
        val tp3 = SimpleTokenParser(tokenType, "foobar", 0)
        val tokenizer = Tokenizer(listOf(tp3, tp2, tp1))

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
        val tokenizer = Tokenizer(listOf(tp3, tp2, tp1))

        val expected = listOf(Token(tokenType, "abc", 0))
        tokenizer.tokenize("abc") shouldBe expected
    }

    @Test
    fun `test skips whitespace between tokens`() {
        val tp = SimpleTokenParser(tokenType, "a")
        val tokenizer = Tokenizer(listOf(tp))

        val expected = listOf(
            Token(tokenType, "a", 2),
            Token(tokenType, "a", 8),
        )
        tokenizer.tokenize("  a \t\r\n a") shouldBe expected
    }

    @Test
    fun `test throws on unknown token with position`() {
        val tp = SimpleTokenParser(tokenType, "a")
        val tokenizer = Tokenizer(listOf(tp))

        val exception = shouldThrow<ExpressionException> {
            tokenizer.tokenize("aa b")
        }
        exception.message shouldBe "unknown token at position 3"
    }
}
