package ru.cramen.suffeeex.core.lexical.v2.tokens

import io.kotest.matchers.shouldBe
import kotlin.test.Test

internal class SimpleMultiTokenParserTest {

    private val tokenType = object : TokenType() {}

    @Test
    fun match() {
        val exp = "hello world"
        val failedExp = "boo world"
        val hello = "hello"
        val world = "world"

        val tp = SimpleMultiTokenParser(
            mapOf(
                "he" to tokenType,
                "hell" to tokenType,
                "hello" to tokenType,
                "helloWorld" to tokenType,
                "world" to tokenType,
            )
        )
        tp.match(exp, 0) shouldBe Token(tokenType, hello, 0);
        tp.match(exp, 6) shouldBe Token(tokenType, world, 6);
        tp.match(failedExp, 0) shouldBe null;
    }
}
