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
import java.util.Locale
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

/**
 * Generic `s.<jvmName>(args...)` string function: emitted as a String method
 * call on bytecode backends, [impl] gives the identical Kotlin semantics for
 * the composition backend. [jvmArgTypes] are the declared JVM parameter types
 * (mind overloads).
 */
private class StringMethodNode(
    private val jvmName: String,
    private val jvmArgTypes: List<KClass<*>>,
    override val type: KClass<*>,
    private val receiver: TypedNode,
    private val args: List<TypedNode>,
    private val impl: (String, List<Any?>) -> Any,
) : TypedNode {
    override fun build(): Expression {
        val r = receiver.build()
        val built = args.map { it.build() }
        return Expression { c -> impl(r.eval(c) as String, built.map { it.eval(c) }) }
    }

    override fun emit(emission: Emission) {
        emission.push(receiver)
        args.forEach { emission.push(it) }
        emission.invokeStringMethod(jvmName, jvmArgTypes, type)
    }
}

/**
 * toUpperCase/toLowerCase: locale-independent case mapping. The bytecode
 * backend passes Locale.ROOT explicitly (String.toUpperCase(Locale)) to avoid
 * the Turkish-i problem of the default-locale overload; the composition
 * backend uses Kotlin's locale-independent uppercase()/lowercase().
 */
private class StringCaseNode(private val upper: Boolean, private val arg: TypedNode) : TypedNode {
    override val type: KClass<*> = String::class

    override fun build(): Expression {
        val e = arg.build()
        return Expression { c ->
            val s = e.eval(c) as String
            if (upper) s.uppercase() else s.lowercase()
        }
    }

    override fun emit(emission: Emission) {
        emission.push(arg)
        emission.getStaticField(Locale::class, "ROOT", Locale::class)
        emission.invokeStringMethod(if (upper) "toUpperCase" else "toLowerCase", listOf(Locale::class), String::class)
    }
}

/** substring(s, begin[, end]): (String, Int[, Int]) -> String. */
class SubstringNode(val source: TypedNode, val begin: TypedNode, val end: TypedNode?) : TypedNode {
    override val type: KClass<*> = String::class

    override fun build(): Expression {
        val s = source.build()
        val b = begin.build()
        val e = end?.build()
        return Expression { c ->
            val str = s.eval(c) as String
            if (e != null) str.substring(b.eval(c) as Int, e.eval(c) as Int)
            else str.substring(b.eval(c) as Int)
        }
    }

    override fun emit(emission: Emission) {
        emission.push(source)
        emission.push(begin)
        if (end != null) {
            emission.push(end)
            emission.invokeStringMethod("substring", listOf(Int::class, Int::class), String::class)
        } else {
            emission.invokeStringMethod("substring", listOf(Int::class), String::class)
        }
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

// substring: String receiver plus Int indices, 2..3 arguments.
private class SubstringFunction : FunctionParser {
    override val name = "substring"
    override val minArgs = 2
    override val maxArgs = 3

    override fun compile(args: List<TypedNode>): TypedNode {
        if (args[0].type != String::class) {
            throw ExpressionException("function 'substring' expects a String receiver, got ${args[0].type.simpleName}")
        }
        args.drop(1).forEach { arg ->
            if (arg.type != Int::class) {
                throw ExpressionException("function 'substring' expects Int indices, got ${arg.type.simpleName}")
            }
        }
        return SubstringNode(args[0], args[1], args.getOrNull(2))
    }
}

// Builds a StringFunction (all-String args) compiling to a StringMethodNode:
// the first expression argument is the receiver, the rest map to jvmArgTypes.
private fun stringMethod(
    name: String,
    jvmArgTypes: List<KClass<*>>,
    resultType: KClass<*>,
    impl: (String, List<Any?>) -> Any,
) = StringFunction(name, jvmArgTypes.size + 1) { args ->
    StringMethodNode(name, jvmArgTypes, resultType, args[0], args.drop(1), impl)
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

        // matches: Java String.matches -- the pattern must match the WHOLE string.
        registry.registerFunction(
            stringMethod("matches", listOf(String::class), Boolean::class) { s, a -> Regex(a[0] as String).matches(s) }
        )
        registry.registerFunction(
            stringMethod("startsWith", listOf(String::class), Boolean::class) { s, a -> s.startsWith(a[0] as String) }
        )
        registry.registerFunction(
            stringMethod("endsWith", listOf(String::class), Boolean::class) { s, a -> s.endsWith(a[0] as String) }
        )
        registry.registerFunction(
            stringMethod("indexOf", listOf(String::class), Int::class) { s, a -> s.indexOf(a[0] as String) }
        )
        registry.registerFunction(SubstringFunction())
        // replace: LITERAL replacement -- String.replace(CharSequence, CharSequence), not replaceAll (no regex).
        registry.registerFunction(
            stringMethod("replace", listOf(CharSequence::class, CharSequence::class), String::class) { s, a ->
                s.replace(a[0] as String, a[1] as String)
            }
        )
        registry.registerFunction(StringFunction("toUpperCase", 1) { StringCaseNode(true, it[0]) })
        registry.registerFunction(StringFunction("toLowerCase", 1) { StringCaseNode(false, it[0]) })
        // trim: Java String.trim semantics (chars <= 0x20), NOT Kotlin's trim() -- both backends must agree.
        registry.registerFunction(
            stringMethod("trim", emptyList(), String::class) { s, _ -> s.trim { it <= ' ' } }
        )
    }
}
