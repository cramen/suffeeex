package ru.cramen.suffeeex.core.lexical.v2.tokens

object VariableTokenType: TokenType()

object VariableTokenParser: RegexpTokenParser(VariableTokenType, "^\\\$[a-zA-Z][_0-9a-zA-Z\\d]*".toRegex(), MEDIUM_TOKEN_PRIORITY)