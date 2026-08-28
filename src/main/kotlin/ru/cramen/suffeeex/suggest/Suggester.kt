package ru.cramen.suffeeex.suggest

import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.FunctionParser
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import ru.cramen.suffeeex.core.token.Tokenizer
import kotlin.reflect.KClass

enum class SuggestionKind { FUNCTION, VARIABLE, KEYWORD, OPERATOR, BRACKET, MEMBER }

data class Suggestion(val text: String, val kind: SuggestionKind, val detail: String? = null)

// the incomplete token being typed at the cursor: an identifier fragment or
// a '$'-prefixed variable fragment (so "$x" matches variable candidates
// directly); digits-only tails are committed literals, not fragments
private val TAIL_REGEX = Regex("(\\\$[A-Za-z0-9_]*|[A-Za-z_][A-Za-z0-9_]*)$")

/**
 * Autocomplete for expression input, driven by the same [ExtensionRegistry]
 * that drives parsing: everything an extension registers (functions,
 * operators, literal keywords, brackets, member access) appears in the
 * suggestions automatically, via the token parsers' `hints()`.
 *
 * The algorithm is a pragmatic v1 without an error-recovery parser: the
 * trailing identifier fragment before the cursor (the "tail") filters
 * candidates by prefix; the committed prefix before it is tokenized, and the
 * last committed token classifies the cursor position as OPERAND (an operand
 * is expected: functions, variables, literal keywords, prefix operators,
 * `(`), AFTER-OPERAND (an operator is expected: infix operators, `)`, `,`,
 * member access `.`), or AMBIGUOUS (union of both). Results are ordered by
 * [SuggestionKind] declaration order (functions first), deduplicated by
 * (text, kind).
 */
class Suggester(private val registry: ExtensionRegistry) {
    private val tokenizer = Tokenizer(registry.tokenParsers)

    private enum class Position { OPERAND, AFTER_OPERAND, AMBIGUOUS }

    /** Token hints grouped by the syntactic role of the token they produce. */
    private class HintRoles {
        val literalKeywords = mutableListOf<String>()
        val prefixOperators = mutableListOf<String>()
        val infixOperators = mutableListOf<String>()
        val memberAccesses = mutableListOf<String>()
        val openBrackets = mutableListOf<String>()
        val closeBracketsAndSeparators = mutableListOf<String>()
    }

    private val hintsByRole: HintRoles by lazy { classifyHints() }

    fun suggest(
        source: String,
        cursor: Int = source.length,
        varTypes: Map<String, KClass<*>> = emptyMap(),
    ): List<Suggestion> {
        val before = source.substring(0, cursor.coerceIn(0, source.length))
        if (insideStringLiteral(before)) return emptyList()

        val tail = TAIL_REGEX.find(before)?.value ?: ""
        val committedPrefix = before.substring(0, before.length - tail.length)
        val tokens = try {
            tokenizer.tokenize(committedPrefix)
        } catch (e: ExpressionException) {
            // unknown committed context: identifier categories only
            return filter(identifierCandidates(varTypes), tail)
        }
        val candidates = when (positionOf(tokens)) {
            Position.OPERAND -> operandCandidates(varTypes)
            Position.AFTER_OPERAND -> afterOperandCandidates()
            Position.AMBIGUOUS -> operandCandidates(varTypes) + afterOperandCandidates()
        }
        return filter(candidates, tail)
    }

    private fun positionOf(tokens: List<Token>): Position {
        val last = tokens.lastOrNull() ?: return Position.OPERAND
        val type = last.type
        val bracket = registry.bracketParser
        return when {
            type in registry.prefixTypes() || type in registry.infixTypes() -> Position.OPERAND
            bracket != null && (type == bracket.openType || type == bracket.separatorType) -> Position.OPERAND
            type in registry.literalTypes() -> Position.AFTER_OPERAND
            bracket != null && type == bracket.closeType -> Position.AFTER_OPERAND
            else -> Position.AMBIGUOUS
        }
    }

    private fun operandCandidates(varTypes: Map<String, KClass<*>>): List<Suggestion> =
        functionSuggestions() +
            varTypes.map { (name, type) -> Suggestion("$$name", SuggestionKind.VARIABLE, type.simpleName) } +
            hintsByRole.literalKeywords.map { Suggestion(it, SuggestionKind.KEYWORD) } +
            hintsByRole.prefixOperators.map { Suggestion(it, SuggestionKind.OPERATOR) } +
            hintsByRole.openBrackets.map { Suggestion(it, SuggestionKind.BRACKET) }

    private fun afterOperandCandidates(): List<Suggestion> =
        hintsByRole.infixOperators.map { Suggestion(it, SuggestionKind.OPERATOR) } +
            hintsByRole.closeBracketsAndSeparators.map { Suggestion(it, SuggestionKind.BRACKET) } +
            hintsByRole.memberAccesses.map { Suggestion(it, SuggestionKind.MEMBER) }

    private fun identifierCandidates(varTypes: Map<String, KClass<*>>): List<Suggestion> =
        functionSuggestions() +
            varTypes.map { (name, type) -> Suggestion("$$name", SuggestionKind.VARIABLE, type.simpleName) } +
            hintsByRole.literalKeywords.map { Suggestion(it, SuggestionKind.KEYWORD) }

    private fun functionSuggestions(): List<Suggestion> =
        registry.allFunctions().map { Suggestion(it.name, SuggestionKind.FUNCTION, signature(it)) }

    /** A synthetic signature from the arity bounds, e.g. `pow(arg1, arg2)` or `substring(arg1, arg2, …)`. */
    private fun signature(f: FunctionParser): String {
        val required = (1..f.minArgs).joinToString(", ") { "arg$it" }
        val optional = if (f.maxArgs > f.minArgs) "…" else null
        val args = listOfNotNull(required.ifEmpty { null }, optional).joinToString(", ")
        return "${f.name}($args)"
    }

    private fun classifyHints(): HintRoles {
        val roles = HintRoles()
        val literalTypes = registry.literalTypes()
        val prefixTypes = registry.prefixTypes()
        val infixTypes = registry.infixTypes()
        val memberTypes = registry.memberAccessTypes()
        val bracket = registry.bracketParser
        for (parser in registry.tokenParsers) {
            for (hint in parser.hints()) {
                val type = singleTokenType(hint) ?: continue
                if (type in literalTypes) roles.literalKeywords += hint
                if (type in prefixTypes) roles.prefixOperators += hint
                if (type in infixTypes) roles.infixOperators += hint
                if (type in memberTypes) roles.memberAccesses += hint
                if (bracket != null) when (type) {
                    bracket.openType -> roles.openBrackets += hint
                    bracket.closeType, bracket.separatorType -> roles.closeBracketsAndSeparators += hint
                }
            }
        }
        return roles
    }

    /** The type a hint tokenizes to on its own; null when it isn't a single token. */
    private fun singleTokenType(hint: String): TokenType? = try {
        tokenizer.tokenize(hint).singleOrNull()?.type
    } catch (e: ExpressionException) {
        null
    }

    /** Heuristic: an odd count of unescaped `"` means the cursor is inside a string literal. */
    private fun insideStringLiteral(s: String): Boolean {
        var quotes = 0
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\') {
                i += 2
                continue
            }
            if (s[i] == '"') quotes++
            i++
        }
        return quotes % 2 == 1
    }

    private fun filter(candidates: List<Suggestion>, tail: String): List<Suggestion> =
        candidates
            .filter { it.text.startsWith(tail) }
            .distinctBy { it.text to it.kind }
            .sortedBy { it.kind.ordinal }
}

/**
 * Suggests completions for [source] at [cursor], driven by this compiler's
 * extension registry. Lives here (not in core) so core never depends on the
 * suggest package.
 */
fun ExpressionCompiler.suggest(
    source: String,
    cursor: Int = source.length,
    varTypes: Map<String, KClass<*>> = emptyMap(),
): List<Suggestion> = Suggester(registry).suggest(source, cursor, varTypes)
