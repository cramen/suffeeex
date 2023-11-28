package ru.cramen.suffeeex.core.lexical.v2.tokens

import io.kotest.matchers.shouldBe
import kotlin.test.Test

internal class SimpleTokenParserTest {

    private val tokenType = object : TokenType() {}

    @Test
    fun match() {
        val exp = "hello world"
        val hello = "hello"
        val world = "world"

        val tpHello = SimpleTokenParser(tokenType, hello)
        val tpWorld = SimpleTokenParser(tokenType, world)
        val tpSpace = SimpleTokenParser(tokenType, " ")
        tpHello.match(exp, 0) shouldBe Token(tokenType, hello, 0)
        tpHello.match(exp, 1) shouldBe null
        tpWorld.match(exp, 0) shouldBe null
        tpWorld.match(exp, 6) shouldBe Token(tokenType, world, 6)
        tpWorld.match("", 6) shouldBe null
        tpSpace.match(exp, 5) shouldBe Token(tokenType, " ", 5)
    }
}
