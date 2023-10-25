package ru.cramen.suffeeex.core.lexical.tokens

abstract class Token(open val value: String) {
    abstract val length: Int
}
abstract class HasValueToken(override val value: String): Token(value) {
    override val length: Int get() = value.length
}
abstract class SimpleToken(override val value: String): Token(value) {
    override val length: Int get() = 1
}
abstract class BraceToken(override val value: String): SimpleToken(value)

data object OpenBraceToken : BraceToken("(")
data object CloseBraceToken : BraceToken(")")
data object OpenSquareBraceToken : BraceToken("[")
data object CloseSquareBraceToken : BraceToken("]")
data object OpenCurlyBraceToken : BraceToken("[")
data object CloseCurlyBraceToken : BraceToken("]")
data object CommaToken : SimpleToken(",")

data class SpaceToken(override val value: String) : HasValueToken(value)
data class NumberToken(override val value: String) : HasValueToken(value)
data class StringToken(override val value: String) : HasValueToken(value)
data class OperatorToken(override val value: String) : HasValueToken(value)
data class VariableToken(override val value: String) : HasValueToken(value)