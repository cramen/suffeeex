package ru.cramen.suffeeex.core.syntax

import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import kotlin.reflect.KClass

class SyntaxParser(private val registry: ExtensionRegistry) {

    fun parse(tokens: List<Token>, varTypes: Map<String, KClass<*>> = emptyMap()): TypedNode {
        val cursor = Cursor(tokens)
        val expression = parseExpression(cursor, 0, varTypes)
        if (cursor.hasNext()) {
            val token = cursor.peek()
            throw ExpressionException("unexpected token '${token.value}' at position ${token.position}")
        }
        return expression
    }

    private fun parseExpression(cursor: Cursor, minPrecedence: Int, varTypes: Map<String, KClass<*>>): TypedNode {
        var left = parsePrimary(cursor, varTypes)
        while (cursor.hasNext()) {
            val infixes = registry.infixOperators(cursor.peek().type)
            if (infixes.isEmpty()) break
            val first = infixes.first()
            if (first.precedence < minPrecedence) break
            cursor.next()
            val rightMinPrecedence = if (first.rightAssociative) first.precedence else first.precedence + 1
            val right = parseExpression(cursor, rightMinPrecedence, varTypes)
            left = compileInfix(infixes, left, right)
        }
        return left
    }

    // Several parsers can be registered for one token (e.g. a numeric "+"
    // and a string "+"): the first whose compile succeeds wins; if all
    // throw ExpressionException, the first one's exception is rethrown.
    private fun compileInfix(infixes: List<InfixOperatorParser>, left: TypedNode, right: TypedNode): TypedNode {
        var firstException: ExpressionException? = null
        for (infix in infixes) {
            try {
                return infix.compile(left, right)
            } catch (exception: ExpressionException) {
                if (firstException == null) firstException = exception
            }
        }
        throw firstException!!
    }

    private fun parsePrimary(cursor: Cursor, varTypes: Map<String, KClass<*>>): TypedNode {
        if (!cursor.hasNext()) {
            throw ExpressionException("unexpected end of expression")
        }
        val token = cursor.peek()
        val bracket = registry.bracketParser

        // checked first: the function-name token type is usually also a variable literal
        if (bracket != null
            && registry.isFunctionNameToken(token.type)
            && cursor.hasNext(1)
            && cursor.peek(1).type == bracket.openType
        ) {
            return parseFunctionCall(cursor, token, bracket, varTypes)
        }

        registry.literal(token.type)?.let { literal ->
            cursor.next()
            return literal.compile(token, varTypes)
        }

        registry.prefixOperator(token.type)?.let { prefix ->
            cursor.next()
            return prefix.compile(parseExpression(cursor, prefix.precedence, varTypes))
        }

        if (bracket != null && token.type == bracket.openType) {
            cursor.next()
            val inner = parseExpression(cursor, 0, varTypes)
            expect(cursor, bracket.closeType, "expected closing bracket")
            return inner
        }

        throw ExpressionException("unexpected token '${token.value}' at position ${token.position}")
    }

    private fun parseFunctionCall(
        cursor: Cursor,
        nameToken: Token,
        bracket: BracketParser,
        varTypes: Map<String, KClass<*>>,
    ): TypedNode {
        cursor.next() // function name
        cursor.next() // open bracket
        val args = mutableListOf<TypedNode>()
        if (cursor.hasNext() && cursor.peek().type != bracket.closeType) {
            args += parseExpression(cursor, 0, varTypes)
            while (cursor.hasNext() && cursor.peek().type == bracket.separatorType) {
                cursor.next()
                args += parseExpression(cursor, 0, varTypes)
            }
        }
        expect(cursor, bracket.closeType, "expected closing bracket after function arguments")

        val function = registry.function(nameToken.value)
            ?: throw ExpressionException("unknown function '${nameToken.value}' at position ${nameToken.position}")
        if (args.size !in function.minArgs..function.maxArgs) {
            throw ExpressionException(
                "function '${function.name}' expects ${function.minArgs}..${function.maxArgs} arguments," +
                    " got ${args.size} at position ${nameToken.position}"
            )
        }
        return function.compile(args)
    }

    private fun expect(cursor: Cursor, type: TokenType, message: String) {
        if (!cursor.hasNext() || cursor.peek().type != type) {
            val where = if (cursor.hasNext()) " at position ${cursor.peek().position}" else " at end of expression"
            throw ExpressionException(message + where)
        }
        cursor.next()
    }

    private class Cursor(private val tokens: List<Token>) {
        private var index = 0

        fun hasNext(offset: Int = 0): Boolean = index + offset < tokens.size

        fun peek(offset: Int = 0): Token = tokens[index + offset]

        fun next(): Token = tokens[index++]
    }
}
