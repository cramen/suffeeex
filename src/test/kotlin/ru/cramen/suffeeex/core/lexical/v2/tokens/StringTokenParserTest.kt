package ru.cramen.suffeeex.core.lexical.v2.tokens

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class StringTokenParserTest {

    @Test
    fun match() {
        val tp = StringTokenParser
        tp.match("\"hello world\"", 0) shouldBe Token(StringTokenType, "\"hello world\"", 0)
        tp.match("\"hello \\\"world\"", 0) shouldBe Token(StringTokenType, "\"hello \"world\"", 0)
        tp.match("space\"hello \\\"world\"", 5) shouldBe Token(StringTokenType, "\"hello \"world\"", 5)
    }
}
