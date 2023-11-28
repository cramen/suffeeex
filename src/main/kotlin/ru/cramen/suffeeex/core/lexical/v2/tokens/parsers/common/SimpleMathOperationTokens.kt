package ru.cramen.suffeeex.core.lexical.v2.tokens

object PlusTokenType: TokenType()
object MinusTokenType: TokenType()
object MultiplyTokenType: TokenType()
object DivideTokenType: TokenType()
object EquelsTokenType: TokenType()

object PercentTokenType: TokenType()

object AndTokenType: TokenType()
object OrTokenType: TokenType()
object NotTokenType: TokenType()

private val simpleMathOperationsTypesMap = mapOf(
    "+" to PlusTokenType,
    "-" to MinusTokenType,
    "*" to MultiplyTokenType,
    "/" to DivideTokenType,
    "=" to EquelsTokenType,
    "%" to PercentTokenType,
    "&" to AndTokenType,
    "|" to OrTokenType,
    "!" to NotTokenType,
)
object SimpleMathOperationsTokenParser: SimpleMultiTokenParser(simpleMathOperationsTypesMap, HIGH_TOKEN_PRIORITY)
