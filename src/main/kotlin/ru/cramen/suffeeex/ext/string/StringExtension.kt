package ru.cramen.suffeeex.ext.string

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.CompareOp
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.FunctionParser
import ru.cramen.suffeeex.core.syntax.InfixOperatorParser
import ru.cramen.suffeeex.core.syntax.LiteralParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.LOW_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import ru.cramen.suffeeex.ext.math.function.IdentifierTokenType
import ru.cramen.suffeeex.ext.math.operator.PlusTokenType
import kotlin.reflect.KClass

object StringTokenType : TokenType()

class StringLiteralNode(val value: String) : TypedNode {
    override val type: KClass<*> = String::class

    override fun build(): Expression = Expression { value }

    override fun emit(emission: Emission) = emission.constant(String::class, value)
}

/** String concatenation: both operands must be String, the result is String. */
class StringConcatNode(val left: TypedNode, val right: TypedNode) : TypedNode {
    override val type: KClass<*> = String::class

    init {
        if (left.type != String::class || right.type != String::class) {
            throw ExpressionException(
                "operator '+' requires both operands to be String, got ${left.type.simpleName} and ${right.type.simpleName}"
            )
        }
    }

    override fun build(): Expression {
        val l = left.build()
        val r = right.build()
        return Expression { c -> (l.eval(c) as String) + (r.eval(c) as String) }
    }

    override fun emit(emission: Emission) {
        emission.push(left)
        emission.push(right)
        emission.stringConcat()
    }
}

/** length(s): String -> Int. */
class StringLengthNode(val arg: TypedNode) : TypedNode {
    override val type: KClass<*> = Int::class

    override fun build(): Expression {
        val e = arg.build()
        return Expression { c -> (e.eval(c) as String).length }
    }

    override fun emit(emission: Emission) {
        emission.push(arg)
        emission.invokeStringMethod("length", emptyList(), Int::class)
    }
}

/**
 * contains(s, sub): (String, String) -> Boolean. `String.contains` takes a
 * CharSequence which the emission layer cannot describe, so it is compiled
 * as `s.indexOf(sub) >= 0`.
 */
class StringContainsNode(val source: TypedNode, val sub: TypedNode) : TypedNode {
    override val type: KClass<*> = Boolean::class

    override fun build(): Expression {
        val s = source.build()
        val e = sub.build()
        return Expression { c -> (s.eval(c) as String).indexOf(e.eval(c) as String) >= 0 }
    }

    override fun emit(emission: Emission) {
        emission.push(source)
        emission.push(sub)
        emission.invokeStringMethod("indexOf", listOf(String::class), Int::class)
        emission.constant(Int::class, 0)
        emission.compare(CompareOp.GE, Int::class)
    }
}

// Unescapes the minimal set (\n, \t, \", \\); any other escaped character is kept as-is.
private fun unescape(raw: String): String {
    if ('\\' !in raw) return raw
    val out = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '\\' && i + 1 < raw.length) {
            when (raw[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                else -> out.append(c).append(raw[i + 1])
            }
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

// length/contains: all arguments must be String; the node fixes the result type.
private class StringFunction(
    override val name: String,
    arity: Int,
    private val node: (List<TypedNode>) -> TypedNode,
) : FunctionParser {
    override val minArgs = arity
    override val maxArgs = arity

    override fun compile(args: List<TypedNode>): TypedNode {
        args.forEach { arg ->
            if (arg.type != String::class) {
                throw ExpressionException("function '$name' expects String arguments, got ${arg.type.simpleName}")
            }
        }
        return node(args)
    }
}

object StringExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        registry.registerTokenParser(
            RegexpTokenParser(StringTokenType, Regex("\"([^\"\\\\]|\\\\.)*\""), LOW_TOKEN_PRIORITY)
        )
        // function-call syntax needs identifier tokens; the shared type from
        // MathFunctionsExtension is reused so both extensions can coexist
        registry.registerTokenParser(
            RegexpTokenParser(IdentifierTokenType, Regex("[a-zA-Z_][a-zA-Z0-9_]*"), LOW_TOKEN_PRIORITY)
        )
        registry.registerFunctionNameTokenType(IdentifierTokenType)
        registry.registerLiteral(object : LiteralParser {
            override val tokenType = StringTokenType

            override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode {
                val raw = token.value.substring(1, token.value.length - 1)
                return StringLiteralNode(unescape(raw))
            }
        })

        // Second parser on '+': the arithmetic one is tried first and rejects
        // non-numeric operands, so string concat only takes over for Strings.
        registry.registerInfixOperator(object : InfixOperatorParser {
            override val tokenType = PlusTokenType
            override val precedence = 10
            override fun compile(left: TypedNode, right: TypedNode): TypedNode = StringConcatNode(left, right)
        })

        registry.registerFunction(StringFunction("length", 1) { StringLengthNode(it[0]) })
        registry.registerFunction(StringFunction("contains", 2) { StringContainsNode(it[0], it[1]) })
    }
}
