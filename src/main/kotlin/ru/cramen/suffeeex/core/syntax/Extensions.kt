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

/**
 * Infix-like member access (e.g. a `.` token): unlike a normal infix
 * operator, the member name is consumed raw (not parsed as an expression)
 * and handed to [compile] together with the already parsed target node.
 * Binds tighter than any infix operator.
 *
 * A token type registered both as member access and as an infix operator is
 * treated as member access (member access is checked first).
 */
interface MemberAccessParser {
    val tokenType: TokenType
    fun compile(target: TypedNode, member: Token): TypedNode
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
