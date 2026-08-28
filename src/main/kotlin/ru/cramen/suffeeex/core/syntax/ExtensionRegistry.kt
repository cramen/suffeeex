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
    private val memberAccesses = mutableMapOf<TokenType, MemberAccessParser>()
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

    fun registerMemberAccess(parser: MemberAccessParser) {
        memberAccesses[parser.tokenType] = parser
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

    /**
     * Member access is checked before infix operators in the parse loop, so
     * a token type registered as both is treated as member access.
     */
    fun memberAccess(type: TokenType): MemberAccessParser? = memberAccesses[type]

    fun function(name: String): FunctionParser? = functions[name]

    fun isFunctionNameToken(type: TokenType): Boolean = type == functionNameTokenType

    /** All registered function parsers, in registration order. Read-only view for tooling. */
    fun allFunctions(): Collection<FunctionParser> = functions.values

    /** Token types with a registered literal parser. Read-only view for tooling. */
    fun literalTypes(): Set<TokenType> = literals.keys

    /** Token types with a registered prefix operator parser. Read-only view for tooling. */
    fun prefixTypes(): Set<TokenType> = prefixOperators.keys

    /** Token types with at least one registered infix operator parser. Read-only view for tooling. */
    fun infixTypes(): Set<TokenType> = infixOperators.keys

    /** Token types with a registered member access parser. Read-only view for tooling. */
    fun memberAccessTypes(): Set<TokenType> = memberAccesses.keys
}
