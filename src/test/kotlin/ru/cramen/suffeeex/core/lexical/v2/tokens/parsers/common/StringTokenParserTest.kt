package ru.cramen.suffeeex.core.lexical.v2.tokens.parsers.common

import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.core.lexical.v2.tokens.StringTokenParser
import ru.cramen.suffeeex.core.lexical.v2.tokens.StringTokenType
import ru.cramen.suffeeex.core.lexical.v2.tokens.Token
import kotlin.test.Test

internal class StringTokenParserTest {

    @Test
    fun match() {
        val tp = StringTokenParser
        tp.match("\"hello world\"", 0) shouldBe Token(StringTokenType, "\"hello world\"", 0)
        tp.match("\"hello \\\"world\"", 0) shouldBe Token(StringTokenType, "\"hello \"world\"", 0)
        tp.match("\"hello", 0) shouldBe null
        tp.match("space\"hello \\\"world\"", 5) shouldBe Token(StringTokenType, "\"hello \"world\"", 5)
    }
}
