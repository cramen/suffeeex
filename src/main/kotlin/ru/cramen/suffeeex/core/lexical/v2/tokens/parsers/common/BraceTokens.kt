package ru.cramen.suffeeex.core.lexical.v2.tokens

object LeftBraceTokenType: TokenType()
object RightBraceTokenType: TokenType()
object LeftSquareBraceTokenType: TokenType()
object RightSquareBraceTokenType: TokenType()
object LeftCurlyBraceTokenType: TokenType()
object RightCurlyBraceTokenType: TokenType()
object LeftTriangleBraceTokenType: TokenType()
object RightTriangleBraceTokenType: TokenType()

private val braceTokenTypesMap = mapOf(
    "(" to LeftBraceTokenType,
    ")" to RightBraceTokenType,
    "[" to LeftSquareBraceTokenType,
    "]" to RightSquareBraceTokenType,
    "{" to LeftCurlyBraceTokenType,
    "}" to RightCurlyBraceTokenType,
    "<" to LeftTriangleBraceTokenType,
    ">" to RightTriangleBraceTokenType,
)
object BraceTokenParser: SimpleMultiTokenParser(braceTokenTypesMap, MEDIUM_TOKEN_PRIORITY)
