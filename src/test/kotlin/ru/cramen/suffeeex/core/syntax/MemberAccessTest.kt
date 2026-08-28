package ru.cramen.suffeeex.core.syntax

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.cramen.suffeeex.core.EvaluationContext
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.CompositionBackend
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.core.token.SimpleTokenParser
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import kotlin.reflect.KClass
import kotlin.test.Test

internal class MemberAccessTest {

    private object LongTokenType : TokenType()
    private object VariableTokenType : TokenType()
    private object PlusTokenType : TokenType()
    private object DotTokenType : TokenType()

    private class FakeLongNode(override val type: KClass<*> = Long::class, val op: (EvaluationContext) -> Long) :
        TypedNode {
        override fun build(): Expression = Expression { context -> op(context) }
    }

    private val testExtension = SyntaxExtension { registry ->
        registry.registerTokenParser(RegexpTokenParser(LongTokenType, Regex("\\d+")))
        registry.registerTokenParser(RegexpTokenParser(VariableTokenType, Regex("[a-zA-Z_][a-zA-Z0-9_]*")))
        registry.registerTokenParser(SimpleTokenParser(PlusTokenType, "+"))
        registry.registerTokenParser(SimpleTokenParser(DotTokenType, "."))

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

        registry.registerInfixOperator(object : InfixOperatorParser {
            override val tokenType = PlusTokenType
            override val precedence = 10
            override fun compile(left: TypedNode, right: TypedNode): TypedNode {
                val l = left as FakeLongNode
                val r = right as FakeLongNode
                return FakeLongNode { context -> l.op(context) + r.op(context) }
            }
        })

        // only the member "length2" is known: target + 100
        registry.registerMemberAccess(object : MemberAccessParser {
            override val tokenType = DotTokenType
            override fun compile(target: TypedNode, member: Token): TypedNode {
                if (member.value != "length2") {
                    throw ExpressionException("unknown member '${member.value}' at position ${member.position}")
                }
                val t = target as FakeLongNode
                return FakeLongNode { context -> t.op(context) + 100 }
            }
        })
    }

    private fun eval(source: String, context: Map<String, Any?> = emptyMap()): Any? =
        ExpressionCompiler(testExtension).compile(source, backend = CompositionBackend).eval(MapEvaluationContext(context))

    @Test
    fun `plain infix expression is unaffected`() {
        eval("1 + 1") shouldBe 2L
    }

    @Test
    fun `member access receives the raw member token`() {
        eval("x.length2", mapOf("x" to 5L)) shouldBe 105L
    }

    @Test
    fun `member access chains`() {
        eval("x.length2.length2", mapOf("x" to 5L)) shouldBe 205L
    }

    @Test
    fun `member access binds tighter than infix`() {
        eval("x.length2 + 1", mapOf("x" to 5L)) shouldBe 106L
    }

    @Test
    fun `throws on missing member name`() {
        val exception = shouldThrow<ExpressionException> { eval("x.") }
        exception.message shouldContain "expected a member name after '.'"
    }

    @Test
    fun `parser error on unknown member`() {
        val exception = shouldThrow<ExpressionException> { eval("x.foo") }
        exception.message shouldContain "unknown member 'foo'"
    }
}
