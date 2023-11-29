package ru.cramen.suffeeex.core.lexical.v2.tokens.parsers.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.core.lexical.v2.tokens.*
import kotlin.test.Test

class OperatorTokenParserTest {

    @Test
    fun match() {
        val helloParser = object : OperatorTokenParser("Hello") {}
        val worldParser = object : OperatorTokenParser("world_123") {}
        val exp = "Hello world_123"
        helloParser.match(exp, 0) shouldBe Token(OperatorTokenType, "Hello", 0)
        helloParser.match(exp, 6) shouldBe null
        worldParser.match(exp, 0) shouldBe null
        worldParser.match(exp, 6) shouldBe Token(OperatorTokenType, "world_123", 6)
    }

    @Test
    fun testConstructionException() {
        shouldThrow<IllegalArgumentException> {
            object : OperatorTokenParser("1a") {}
        }
        shouldThrow<IllegalArgumentException> {
            object : OperatorTokenParser("_a") {}
        }
        shouldThrow<IllegalArgumentException> {
            object : OperatorTokenParser("+") {}
        }
    }
}
