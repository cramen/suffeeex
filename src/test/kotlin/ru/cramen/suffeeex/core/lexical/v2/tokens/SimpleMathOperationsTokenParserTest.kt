package ru.cramen.suffeeex.core.lexical.v2.tokens

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import ru.cramen.suffeeex.core.lexical.v2.Tokenizer

internal class SimpleMathOperationsTokenParserTest {

    @Test
    fun match() {
        val tokenizer = Tokenizer(listOf(SimpleMathOperationsTokenParser))
        val tokens = tokenizer.tokenize("+-*/=%&|!")

        tokens shouldHaveSize 9
        tokens shouldBe listOf(
            Token(PlusTokenType, "+", 0),
            Token(MinusTokenType, "-", 1),
            Token(MultiplyTokenType, "*", 2),
            Token(DivideTokenType, "/", 3),
            Token(EquelsTokenType, "=", 4),
            Token(PercentTokenType, "%", 5),
            Token(AndTokenType, "&", 6),
            Token(OrTokenType, "|", 7),
            Token(NotTokenType, "!", 8),
        )
    }
}
