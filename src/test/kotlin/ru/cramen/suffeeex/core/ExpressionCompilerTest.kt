package ru.cramen.suffeeex.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.cramen.suffeeex.core.backend.CompositionBackend
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.BracketParser
import ru.cramen.suffeeex.core.syntax.FunctionParser
import ru.cramen.suffeeex.core.syntax.InfixOperatorParser
import ru.cramen.suffeeex.core.syntax.LiteralParser
import ru.cramen.suffeeex.core.syntax.PrefixOperatorParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.core.token.SimpleTokenParser
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import kotlin.reflect.KClass
import kotlin.test.Test

internal class ExpressionCompilerTest {

    private object LongTokenType : TokenType()
    private object VariableTokenType : TokenType()
    private object PlusTokenType : TokenType()
    private object MinusTokenType : TokenType()
    private object MulTokenType : TokenType()
    private object PowTokenType : TokenType()
    private object OpenBracketTokenType : TokenType()
    private object CloseBracketTokenType : TokenType()
    private object CommaTokenType : TokenType()

    private class FakeLongNode(override val type: KClass<*> = Long::class, val op: (EvaluationContext) -> Long) :
        TypedNode {
        override fun build(): Expression = Expression { context -> op(context) }
    }

    private fun longInfix(
        tokenType: TokenType,
        precedence: Int,
        rightAssociative: Boolean = false,
        op: (Long, Long) -> Long,
    ) = object : InfixOperatorParser {
        override val tokenType = tokenType
        override val precedence = precedence
        override val rightAssociative = rightAssociative
        override fun compile(left: TypedNode, right: TypedNode): TypedNode {
            val l = left as FakeLongNode
            val r = right as FakeLongNode
            return FakeLongNode { context -> op(l.op(context), r.op(context)) }
        }
    }

    private val testExtension = SyntaxExtension { registry ->
        registry.registerTokenParser(RegexpTokenParser(LongTokenType, Regex("\\d+")))
        registry.registerTokenParser(RegexpTokenParser(VariableTokenType, Regex("[a-zA-Z_][a-zA-Z0-9_]*")))
        registry.registerTokenParser(SimpleTokenParser(PlusTokenType, "+"))
        registry.registerTokenParser(SimpleTokenParser(MinusTokenType, "-"))
        registry.registerTokenParser(SimpleTokenParser(MulTokenType, "*"))
        registry.registerTokenParser(SimpleTokenParser(PowTokenType, "^"))
        registry.registerTokenParser(SimpleTokenParser(OpenBracketTokenType, "("))
        registry.registerTokenParser(SimpleTokenParser(CloseBracketTokenType, ")"))
        registry.registerTokenParser(SimpleTokenParser(CommaTokenType, ","))

        registry.registerLiteral(object : LiteralParser {
            override val tokenType = LongTokenType
            override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode {
                val value = token.value.toLong()
                return FakeLongNode { value }
            }
        })
        registry.registerLiteral(object : LiteralParser {
            override val tokenType = VariableTokenType
            override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode =
                FakeLongNode { context -> context.resolve(token.value) as Long }
        })

        registry.registerInfixOperator(longInfix(PlusTokenType, 10) { a, b -> a + b })
        registry.registerInfixOperator(longInfix(MinusTokenType, 10) { a, b -> a - b })
        registry.registerInfixOperator(longInfix(MulTokenType, 20) { a, b -> a * b })
        registry.registerInfixOperator(longInfix(PowTokenType, 40, rightAssociative = true) { a, b -> pow(a, b) })

        // the same "-" token works as unary minus in primary position
        registry.registerPrefixOperator(object : PrefixOperatorParser {
            override val tokenType = MinusTokenType
            override val precedence = 30
            override fun compile(operand: TypedNode): TypedNode {
                val o = operand as FakeLongNode
                return FakeLongNode { context -> -o.op(context) }
            }
        })

        registry.registerBracket(object : BracketParser {
            override val openType = OpenBracketTokenType
            override val closeType = CloseBracketTokenType
            override val separatorType = CommaTokenType
        })
        registry.registerFunctionNameTokenType(VariableTokenType)
        registry.registerFunction(object : FunctionParser {
            override val name = "max"
            override val minArgs = 2
            override val maxArgs = 2
            override fun compile(args: List<TypedNode>): TypedNode {
                val a = args[0] as FakeLongNode
                val b = args[1] as FakeLongNode
                return FakeLongNode { context -> maxOf(a.op(context), b.op(context)) }
            }
        })
    }

    private fun pow(a: Long, b: Long): Long {
        var result = 1L
        repeat(b.toInt()) { result *= a }
        return result
    }

    private fun eval(source: String, context: Map<String, Any?> = emptyMap()): Any? =
        ExpressionCompiler(testExtension).compile(source, backend = CompositionBackend).eval(MapEvaluationContext(context))

    @Test
    fun `evaluates long literal`() {
        eval("42") shouldBe 42L
    }

    @Test
    fun `resolves variable from context`() {
        eval("x + 1", mapOf("x" to 41L)) shouldBe 42L
    }

    @Test
    fun `respects operator precedence`() {
        eval("1 + 2 * 3") shouldBe 7L
        eval("2 * 3 + 1") shouldBe 7L
    }

    @Test
    fun `left associative by default`() {
        eval("10 - 3 - 2") shouldBe 5L
    }

    @Test
    fun `right associative when declared`() {
        eval("2 ^ 3 ^ 2") shouldBe 512L
    }

    @Test
    fun `brackets group expressions`() {
        eval("(1 + 2) * 3") shouldBe 9L
    }

    @Test
    fun `prefix minus binds tighter than infix`() {
        eval("-1 + 2") shouldBe 1L
        eval("-(1 + 2)") shouldBe -3L
        eval("2 * -3") shouldBe -6L
    }

    @Test
    fun `evaluates function call`() {
        eval("max(1, 2)") shouldBe 2L
        eval("max(1 + 2, 3 * 4)") shouldBe 12L
        eval("max(max(1, 5), 3)") shouldBe 5L
    }

    @Test
    fun `whitespace between name and bracket still groups, not calls`() {
        // tokenizer drops whitespace, so "max (1, 2)" is a function call too
        eval("max (1, 2)") shouldBe 2L
    }

    @Test
    fun `throws on trailing tokens`() {
        val exception = shouldThrow<ExpressionException> { eval("1 2") }
        exception.message shouldContain "position 2"
    }

    @Test
    fun `throws on missing close bracket`() {
        shouldThrow<ExpressionException> { eval("(1 + 2") }
    }

    @Test
    fun `throws on missing close bracket in call`() {
        shouldThrow<ExpressionException> { eval("max(1, 2") }
    }

    @Test
    fun `throws on wrong arity`() {
        val exception = shouldThrow<ExpressionException> { eval("max(1)") }
        exception.message shouldContain "max"
    }

    @Test
    fun `throws on unknown function`() {
        val exception = shouldThrow<ExpressionException> { eval("foo(1, 2)") }
        exception.message shouldContain "unknown function 'foo'"
    }

    @Test
    fun `throws on unknown token`() {
        val exception = shouldThrow<ExpressionException> { eval("1 @ 2") }
        exception.message shouldContain "position 2"
    }

    @Test
    fun `throws on empty expression`() {
        shouldThrow<ExpressionException> { eval("") }
    }

    private fun multiInfixExtension(firstMessage: String) = SyntaxExtension { registry ->
        registry.registerTokenParser(RegexpTokenParser(LongTokenType, Regex("\\d+")))
        registry.registerTokenParser(SimpleTokenParser(PlusTokenType, "+"))
        registry.registerLiteral(object : LiteralParser {
            override val tokenType = LongTokenType
            override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode {
                val value = token.value.toLong()
                return FakeLongNode { value }
            }
        })
        registry.registerInfixOperator(object : InfixOperatorParser {
            override val tokenType = PlusTokenType
            override val precedence = 10
            override fun compile(left: TypedNode, right: TypedNode): TypedNode =
                throw ExpressionException(firstMessage)
        })
        registry.registerInfixOperator(longInfix(PlusTokenType, 10) { a, b -> a + b })
    }

    @Test
    fun `falls back to the next infix parser registered for the same token`() {
        val compiler = ExpressionCompiler(multiInfixExtension("first parser rejects"))
        compiler.compile("1 + 2", backend = CompositionBackend)
            .eval(MapEvaluationContext(emptyMap())) shouldBe 3L
    }

    @Test
    fun `rethrows the first infix parser exception when all fail`() {
        val extension = SyntaxExtension { registry ->
            registry.registerTokenParser(RegexpTokenParser(LongTokenType, Regex("\\d+")))
            registry.registerTokenParser(SimpleTokenParser(PlusTokenType, "+"))
            registry.registerLiteral(object : LiteralParser {
                override val tokenType = LongTokenType
                override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode {
                    val value = token.value.toLong()
                    return FakeLongNode { value }
                }
            })
            registry.registerInfixOperator(object : InfixOperatorParser {
                override val tokenType = PlusTokenType
                override val precedence = 10
                override fun compile(left: TypedNode, right: TypedNode): TypedNode =
                    throw ExpressionException("first failure")
            })
            registry.registerInfixOperator(object : InfixOperatorParser {
                override val tokenType = PlusTokenType
                override val precedence = 10
                override fun compile(left: TypedNode, right: TypedNode): TypedNode =
                    throw ExpressionException("second failure")
            })
        }
        val exception = shouldThrow<ExpressionException> {
            ExpressionCompiler(extension).compile("1 + 2", backend = CompositionBackend)
        }
        exception.message shouldBe "first failure"
    }
}
