package ru.cramen.suffeeex.core.syntax

import ru.cramen.suffeeex.core.node.TypeEmission
import ru.cramen.suffeeex.core.node.TypeEmissions
import ru.cramen.suffeeex.core.token.TokenParser
import ru.cramen.suffeeex.core.token.TokenType

fun interface SyntaxExtension {
    fun register(registry: ExtensionRegistry)
}

class ExtensionRegistry {
    val tokenParsers = mutableListOf<TokenParser>()

    /**
     * Type emissions visible to expressions compiled through this registry,
     * falling back to the built-in types. Extensions register their types
     * here via [registerTypeEmission]; other compilers are unaffected.
     */
    val typeEmissions = TypeEmissions(TypeEmissions.DEFAULT)

    private val literals = mutableMapOf<TokenType, LiteralParser>()
    private val prefixOperators = mutableMapOf<TokenType, PrefixOperatorParser>()
    private val infixOperators = mutableMapOf<TokenType, MutableList<InfixOperatorParser>>()
    private val functions = mutableMapOf<String, FunctionParser>()

    // Tokens of this type start a function call when immediately followed by
    // the bracket open token; the token value is used as the function name.
    // One shared identifier token type is enough for now.
    var functionNameTokenType: TokenType? = null
        private set

    var bracketParser: BracketParser? = null
        private set

    fun registerTokenParser(parser: TokenParser) {
        tokenParsers += parser
    }

    fun registerTypeEmission(support: TypeEmission) {
        typeEmissions.register(support)
    }

    fun registerLiteral(parser: LiteralParser) {
        literals[parser.tokenType] = parser
    }

    fun registerPrefixOperator(parser: PrefixOperatorParser) {
        prefixOperators[parser.tokenType] = parser
    }

    fun registerInfixOperator(parser: InfixOperatorParser) {
        infixOperators.getOrPut(parser.tokenType) { mutableListOf() } += parser
    }

    fun registerFunction(parser: FunctionParser) {
        functions[parser.name] = parser
    }

    fun registerBracket(parser: BracketParser) {
        bracketParser = parser
    }

    fun registerFunctionNameTokenType(type: TokenType) {
        functionNameTokenType = type
    }

    fun literal(type: TokenType): LiteralParser? = literals[type]

    fun prefixOperator(type: TokenType): PrefixOperatorParser? = prefixOperators[type]

    /**
     * All infix parsers registered for [type], in registration order.
     * The first whose `compile` succeeds wins; all share the precedence of
     * the first registered one.
     */
    fun infixOperators(type: TokenType): List<InfixOperatorParser> =
        infixOperators[type] ?: emptyList()

    fun function(name: String): FunctionParser? = functions[name]

    fun isFunctionNameToken(type: TokenType): Boolean = type == functionNameTokenType
}
