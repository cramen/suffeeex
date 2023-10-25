package ru.cramen.suffeeex.core.lexical

import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.core.lexical.tokens.*
import kotlin.test.Test


internal class TokenizerTest {

    @Test
    fun `test one tokens`() {
        val tokenizer = Tokenizer()

        tokenizer.tokenize("(") shouldBe listOf(OpenBraceToken)
        tokenizer.tokenize(")") shouldBe listOf(CloseBraceToken)
        tokenizer.tokenize("[") shouldBe listOf(OpenSquareBraceToken)
        tokenizer.tokenize("]") shouldBe listOf(CloseSquareBraceToken)
        tokenizer.tokenize("{") shouldBe listOf(OpenCurlyBraceToken)
        tokenizer.tokenize("}") shouldBe listOf(CloseCurlyBraceToken)
        tokenizer.tokenize(",") shouldBe listOf(CommaToken)
        tokenizer.tokenize("\r\n\t ") shouldBe listOf(SpaceToken("\r\n\t "))
        tokenizer.tokenize("123.456") shouldBe listOf(NumberToken("123.456"))
        tokenizer.tokenize("123") shouldBe listOf(NumberToken("123"))
        tokenizer.tokenize("\"123\\\"qwe!@#$%^&*()-=_+`~|!№%:,.;\"") shouldBe listOf(StringToken("\"123\\\"qwe!@#$%^&*()-=_+`~|!№%:,.;\""))
        tokenizer.tokenize("q123w_E") shouldBe listOf(OperatorToken("q123w_E"))
        tokenizer.tokenize("\$q123w_E") shouldBe listOf(VariableToken("\$q123w_E"))
    }
}