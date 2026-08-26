package ru.cramen.suffeeex.core.syntax

import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import kotlin.reflect.KClass

interface LiteralParser {
    val tokenType: TokenType
    fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode
}

interface PrefixOperatorParser {
    val tokenType: TokenType
    val precedence: Int
    fun compile(operand: TypedNode): TypedNode
}

interface InfixOperatorParser {
    val tokenType: TokenType
    val precedence: Int
    val rightAssociative: Boolean get() = false
    fun compile(left: TypedNode, right: TypedNode): TypedNode
}

interface FunctionParser {
    val name: String
    val minArgs: Int
    val maxArgs: Int
    fun compile(args: List<TypedNode>): TypedNode
}

interface BracketParser {
    val openType: TokenType
    val closeType: TokenType
    val separatorType: TokenType
}
