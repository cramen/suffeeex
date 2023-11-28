package ru.cramen.suffeeex.core.lexical.v2.tokens

object SpaceTokenType: TokenType()
object CommaTokenType: TokenType()

private val delimiterTypesMap = mapOf(
    " " to SpaceTokenType,
    "\t" to SpaceTokenType,
    "\r" to SpaceTokenType,
    "\n" to SpaceTokenType,
    "," to CommaTokenType,
)
object DelimiterTokenParser: SimpleMultiTokenParser(delimiterTypesMap, HIGH_TOKEN_PRIORITY)
