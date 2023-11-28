package ru.cramen.suffeeex.core.lexical.v2.tokens

import io.kotest.matchers.shouldBe
import kotlin.test.Test

internal class RegexpTokenParserTest {

    private val tokenType = object : TokenType() {}

    @Test
    fun match() {
        val exp = "hello world"
        val hello = "hello"
        val ello = "ello"
        val world = "world"

        val tp = RegexpTokenParser(tokenType, "^[a-zA-Z_]+".toRegex())
        tp.match(exp, 0) shouldBe Token(tokenType, hello, 0)
        tp.match(exp, 1) shouldBe Token(tokenType, ello, 1)
        tp.match(exp, 5) shouldBe null
        tp.match(exp, 6) shouldBe Token(tokenType, world, 6)
    }
}
