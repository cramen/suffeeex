package ru.cramen.suffeeex.core.lexical.v2.tokens

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.cramen.suffeeex.core.lexical.v2.Tokenizer

internal class SimpleMathOperationsTokenParserTest {

    @Test
    fun match() {
        val tokenizer = Tokenizer(listOf(SimpleMathOperationsTokenParser))
        val tokens = tokenizer.tokenize("+-*/=%&and|or!not")

        tokens shouldHaveSize 12
        tokens shouldBe listOf(
            Token(PlusTokenType, "+", 0),
            Token(MinusTokenType, "-", 1),
            Token(MultiplyTokenType, "*", 2),
            Token(DivideTokenType, "/", 3),
            Token(EquelsTokenType, "=", 4),
            Token(PercentTokenType, "%", 5),
            Token(AndTokenType, "&", 6),
            Token(AndTokenType, "and", 7),
            Token(OrTokenType, "|", 10),
            Token(OrTokenType, "or", 11),
            Token(NotTokenType, "!", 13),
            Token(NotTokenType, "not", 14),
        )
    }
}
