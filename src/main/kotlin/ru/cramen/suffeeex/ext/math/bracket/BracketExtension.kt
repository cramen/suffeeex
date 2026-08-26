package ru.cramen.suffeeex.ext.math.bracket

import ru.cramen.suffeeex.core.syntax.BracketParser
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.LOW_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.SimpleMultiTokenParser
import ru.cramen.suffeeex.core.token.TokenType

object OpenBraceTokenType : TokenType()
object CloseBraceTokenType : TokenType()
object CommaTokenType : TokenType()

object BracketExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        registry.registerTokenParser(
            SimpleMultiTokenParser(
                mapOf(
                    "(" to OpenBraceTokenType,
                    ")" to CloseBraceTokenType,
                    "," to CommaTokenType,
                ),
                LOW_TOKEN_PRIORITY,
            )
        )
        registry.registerBracket(object : BracketParser {
            override val openType = OpenBraceTokenType
            override val closeType = CloseBraceTokenType
            override val separatorType = CommaTokenType
        })
    }
}
