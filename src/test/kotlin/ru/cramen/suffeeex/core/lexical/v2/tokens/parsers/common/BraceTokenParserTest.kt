package ru.cramen.suffeeex.core.lexical.v2.tokens.parsers.common

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.core.lexical.v2.Tokenizer
import ru.cramen.suffeeex.core.lexical.v2.tokens.*
import kotlin.test.Test

internal class BraceTokenParserTest {

    @Test
    fun match() {
        val tokenizer = Tokenizer(listOf(BraceTokenParser))
        val tokens = tokenizer.tokenize("()[]{}<>")
        tokens shouldHaveSize 8
        tokens shouldBe listOf(
            Token(LeftBraceTokenType, "(", 0),
            Token(RightBraceTokenType, ")", 1),
            Token(LeftSquareBraceTokenType, "[", 2),
            Token(RightSquareBraceTokenType, "]", 3),
            Token(LeftCurlyBraceTokenType, "{", 4),
            Token(RightCurlyBraceTokenType, "}", 5),
            Token(LeftTriangleBraceTokenType, "<", 6),
            Token(RightTriangleBraceTokenType, ">", 7),
        )
    }
}
