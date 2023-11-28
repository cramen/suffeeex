package ru.cramen.suffeeex.core.lexical.v2.tokens

object NumberTokenType: TokenType()

object NumberTokenParser: RegexpTokenParser(NumberTokenType, "^(-)?\\d+(\\.\\d+)?".toRegex(), MEDIUM_TOKEN_PRIORITY)
