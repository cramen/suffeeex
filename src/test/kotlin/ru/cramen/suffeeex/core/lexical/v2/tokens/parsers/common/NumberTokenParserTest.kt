package ru.cramen.suffeeex.core.lexical.v2.tokens.parsers.common

import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.core.lexical.v2.tokens.NumberTokenParser
import ru.cramen.suffeeex.core.lexical.v2.tokens.NumberTokenType
import ru.cramen.suffeeex.core.lexical.v2.tokens.Token
import kotlin.test.Test

internal class NumberTokenParserTest {

    @Test
    fun match() {
        val numStr = "0 1 123 -123 0.0 0.123 -1.123"
        NumberTokenParser.match(numStr, 0) shouldBe Token(NumberTokenType, "0", 0)
        NumberTokenParser.match(numStr, 2) shouldBe Token(NumberTokenType, "1", 2)
        NumberTokenParser.match(numStr, 4) shouldBe Token(NumberTokenType, "123", 4)
        NumberTokenParser.match(numStr, 8) shouldBe Token(NumberTokenType, "-123", 8)
        NumberTokenParser.match(numStr, 13) shouldBe Token(NumberTokenType, "0.0", 13)
        NumberTokenParser.match(numStr, 17) shouldBe Token(NumberTokenType, "0.123", 17)
        NumberTokenParser.match(numStr, 23) shouldBe Token(NumberTokenType, "-1.123", 23)
    }
}
