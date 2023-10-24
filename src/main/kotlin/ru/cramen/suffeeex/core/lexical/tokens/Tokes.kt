package ru.cramen.suffeeex.core.lexical.tokens

abstract class Token(
    open val value: String,
) {
    val length: Int get() { return value.length }
}

data object OpenBracketToken : Token("(")
data object CloseBracketToken : Token(")")
data object OpenSquareBracketToken : Token("[")
data object CloseSquareBracketToken : Token("]")
data object CommaToken : Token(",")

data class SpaceToken(override val value: String) : Token(value)
data class NumberToken(override val value: String) : Token(value)
data class StringToken(override val value: String) : Token(value)
data class OperatorToken(override val value: String) : Token(value)
data class VariableToken(override val value: String) : Token(value)