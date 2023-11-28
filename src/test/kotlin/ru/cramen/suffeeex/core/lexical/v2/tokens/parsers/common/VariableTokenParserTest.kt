package ru.cramen.suffeeex.core.lexical.v2.tokens.parsers.common

import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.core.lexical.v2.tokens.Token
import ru.cramen.suffeeex.core.lexical.v2.tokens.VariableTokenParser
import ru.cramen.suffeeex.core.lexical.v2.tokens.VariableTokenType
import kotlin.test.Test


internal class VariableTokenParserTest {

    @Test
    fun match() {
        val tp = VariableTokenParser
        tp.match("\$var", 0) shouldBe Token(VariableTokenType, "\$var", 0)
        tp.match("\$var1", 0) shouldBe Token(VariableTokenType, "\$var1", 0)
        tp.match("\$foo_bar", 0) shouldBe Token(VariableTokenType, "\$foo_bar", 0)
        tp.match("\$FOO_BAR", 0) shouldBe Token(VariableTokenType, "\$FOO_BAR", 0)
        tp.match("\$foo1_bar2", 0) shouldBe Token(VariableTokenType, "\$foo1_bar2", 0)
        tp.match("\$foo1_bar2+10", 0) shouldBe Token(VariableTokenType, "\$foo1_bar2", 0)
        tp.match("11+\$foo1_bar2+10", 3) shouldBe Token(VariableTokenType, "\$foo1_bar2", 3)
    }
}