package ru.cramen.suffeeex.extensibility

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.CompositionBackend
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.core.backend.asm.AsmBackend
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.StackCategory
import ru.cramen.suffeeex.core.node.TypeEmission
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.InfixOperatorParser
import ru.cramen.suffeeex.core.syntax.LiteralParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.HIGH_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import ru.cramen.suffeeex.ext.math.operator.ArithmeticExtension
import ru.cramen.suffeeex.ext.math.operator.PlusTokenType
import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * Extensibility proof: a whole new value type (literals, operator, bytecode
 * emission) added from test scope, without touching core.
 */

object BigDecimalTokenType : TokenType()

class BigDecimalLiteralNode(val value: BigDecimal) : TypedNode {
    override val type: KClass<*> = BigDecimal::class

    override fun build(): Expression = Expression { value }

    override fun emit(emission: Emission) = emission.constant(BigDecimal::class, value)
}

/** BigDecimal addition: both operands must be BigDecimal, the result is BigDecimal. */
class BigDecimalAddNode(val left: TypedNode, val right: TypedNode) : TypedNode {
    override val type: KClass<*> = BigDecimal::class

    init {
        if (left.type != BigDecimal::class || right.type != BigDecimal::class) {
            throw ExpressionException(
                "operator '+' requires both operands to be BigDecimal," +
                    " got ${left.type.simpleName} and ${right.type.simpleName}"
            )
        }
    }

    override fun build(): Expression {
        val l = left.build()
        val r = right.build()
        return Expression { c -> (l.eval(c) as BigDecimal).add(r.eval(c) as BigDecimal) }
    }

    override fun emit(emission: Emission) {
        emission.push(left)
        emission.push(right)
        emission.invokeVirtual(BigDecimal::class, "add", listOf(BigDecimal::class), BigDecimal::class)
    }
}

object BigDecimalExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        // BigDecimal constants cannot go through LDC: construct them from their string form.
        // Scoped to this registry: other compilers keep the reference-type fallback.
        registry.registerTypeEmission(object : TypeEmission {
            override val type = BigDecimal::class
            override val descriptor = "Ljava/math/BigDecimal;"
            override val category = StackCategory.REFERENCE
            override val wrapperInternalName: String? = null
            override val unboxMethod: String? = null
            override fun pushConstant(emission: Emission, value: Any) {
                emission.newObject(BigDecimal::class)
                emission.ldc(value.toString())
                emission.invokeConstructor(BigDecimal::class, listOf(String::class))
            }
        })

        // HIGH priority: the number literal regex (MEDIUM) would otherwise grab
        // the digits of "1.5bd" and leave "bd" dangling.
        registry.registerTokenParser(
            RegexpTokenParser(BigDecimalTokenType, Regex("\\d+(\\.\\d+)?bd"), HIGH_TOKEN_PRIORITY)
        )
        registry.registerLiteral(object : LiteralParser {
            override val tokenType = BigDecimalTokenType

            override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode =
                BigDecimalLiteralNode(BigDecimal(token.value.dropLast(2)))
        })

        // Second parser on '+': the arithmetic one is tried first and rejects
        // non-numeric operands, so BigDecimal addition only takes over for BigDecimals.
        registry.registerInfixOperator(object : InfixOperatorParser {
            override val tokenType = PlusTokenType
            override val precedence = 10
            override fun compile(left: TypedNode, right: TypedNode): TypedNode = BigDecimalAddNode(left, right)
        })
    }
}

internal class BigDecimalTypeTest {

    private val compiler = ExpressionCompiler(NumberExtension, ArithmeticExtension, BigDecimalExtension)

    private fun eval(source: String, backend: ExpressionBackend): Any? =
        compiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    @Test
    fun `bigdecimal literal evaluates to bigdecimal`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1.5bd", backend) shouldBe BigDecimal("1.5")
        }
    }

    @Test
    fun `bigdecimal addition`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1.5bd + 2.25bd", backend) shouldBe BigDecimal("3.75")
        }
    }

    @Test
    fun `int literals still compile alongside the bigdecimal extension`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("2+3*4", backend) shouldBe 14
        }
    }

    @Test
    fun `a compiler without the extension cannot parse bigdecimal literals`() {
        val plain = ExpressionCompiler(NumberExtension, ArithmeticExtension)
        shouldThrow<ExpressionException> {
            plain.compile("1.5bd")
        }
    }

    @Test
    fun `the bigdecimal type emission does not leak into other compilers`() {
        val plain = ExpressionCompiler(NumberExtension, ArithmeticExtension)
        // the tree comes from the extension compiler; a compiler whose
        // registry lacks the BigDecimal emission falls back to the reference
        // type emission, which cannot push constants on the asm backend
        val tree = compiler.parseTree("1.5bd")
        plain.compileTree(tree, CompositionBackend)
            .eval(MapEvaluationContext(emptyMap())) shouldBe BigDecimal("1.5")
        val exception = shouldThrow<ExpressionException> {
            plain.compileTree(tree, AsmBackend)
        }
        exception.message shouldContain "BigDecimal"
    }
}
