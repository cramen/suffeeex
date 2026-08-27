package ru.cramen.suffeeex.ext.calculus

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.core.backend.asm.AsmBackend
import ru.cramen.suffeeex.core.node.DifferentiableNode
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.NumericOp
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.node.differentiateOrThrow
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.FunctionParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.ext.logic.LogicExtension
import ru.cramen.suffeeex.ext.math.MathSyntax
import ru.cramen.suffeeex.ext.math.number.NumberLiteralNode
import ru.cramen.suffeeex.ext.math.operator.BinaryArithmeticNode
import ru.cramen.suffeeex.ext.string.StringExtension
import ru.cramen.suffeeex.ext.variable.VariableNode
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.reflect.KClass
import kotlin.test.Test

internal class DifferentiatorTest {

    fun interface D1 {
        fun eval(x: Double): Double
    }

    /**
     * Test-only differentiable node: `cube(x)` = x³, built from and
     * differentiated into the existing public node classes.
     */
    private class CubeNode(private val arg: TypedNode) : TypedNode, DifferentiableNode {
        private val body = BinaryArithmeticNode(
            NumericOp.MUL,
            BinaryArithmeticNode(NumericOp.MUL, arg, arg),
            arg,
        )

        override val type: KClass<*> = body.type

        override fun build(): Expression = body.build()

        override fun emit(emission: Emission) = body.emit(emission)

        // d/dx arg³ = 3 · arg² · arg'
        override fun differentiate(by: String): TypedNode = BinaryArithmeticNode(
            NumericOp.MUL,
            BinaryArithmeticNode(
                NumericOp.MUL,
                NumberLiteralNode(3.0, Double::class),
                BinaryArithmeticNode(NumericOp.MUL, arg, arg),
            ),
            arg.differentiateOrThrow(by),
        )
    }

    /** Test-only extension registering `cube`; the function-call syntax comes from MathSyntax. */
    private object CubeExtension : SyntaxExtension {
        override fun register(registry: ExtensionRegistry) {
            registry.registerFunction(object : FunctionParser {
                override val name = "cube"
                override val minArgs = 1
                override val maxArgs = 1
                override fun compile(args: List<TypedNode>): TypedNode = CubeNode(args.single())
            })
        }
    }

    private val compiler = ExpressionCompiler(MathSyntax)
    private val xDouble = mapOf<String, KClass<*>>("x" to Double::class)

    private fun derivative(
        source: String,
        backend: ExpressionBackend,
        varTypes: Map<String, KClass<*>> = xDouble,
        by: String = "x",
    ): Expression = compiler.compileTree(
        Differentiator.differentiate(compiler.parseTree(source, varTypes), by),
        backend,
    )

    @Test
    fun `derivative of 2x + 1 simplifies to the constant 2`() {
        val tree = Differentiator.differentiate(compiler.parseTree("2.0 * \$x + 1.0", xDouble), "x")
        tree.shouldBeInstanceOf<NumberLiteralNode>()
        tree.type shouldBe Double::class
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                compiler.compileTree(tree, backend)
                    .eval(MapEvaluationContext(mapOf("x" to 7.0))) shouldBe 2.0
            }
        }
    }

    @Test
    fun `simplify folds zero and one multiplication identities`() {
        val zero = Differentiator.simplify(compiler.parseTree("0.0 * \$x", xDouble))
        zero.shouldBeInstanceOf<NumberLiteralNode>()
        (zero as NumberLiteralNode).value shouldBe 0.0

        val one = Differentiator.simplify(compiler.parseTree("1.0 * \$x", xDouble))
        one.shouldBeInstanceOf<VariableNode>()
    }

    @Test
    fun `derivative of sin(x) * x matches the product rule`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val d = derivative("sin(\$x) * \$x", backend)
                val x = 0.8
                (d.eval(MapEvaluationContext(mapOf("x" to x))) as Double) shouldBe
                    (sin(x) + x * cos(x) plusOrMinus 1e-9)
            }
        }
    }

    @Test
    fun `derivative of x cubed via pow is 3x squared`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val d = derivative("pow(\$x, 3.0)", backend)
                (d.eval(MapEvaluationContext(mapOf("x" to 2.0))) as Double) shouldBe (12.0 plusOrMinus 1e-9)
            }
        }
    }

    @Test
    fun `derivative of exp(x) over x matches the quotient rule`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val d = derivative("exp(\$x) / \$x", backend)
                val x = 1.5
                val expected = (exp(x) * x - exp(x)) / (x * x)
                (d.eval(MapEvaluationContext(mapOf("x" to x))) as Double) shouldBe (expected plusOrMinus 1e-9)
            }
        }
    }

    @Test
    fun `derivative of ln(x) is 1 over x`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val d = derivative("ln(\$x)", backend)
                (d.eval(MapEvaluationContext(mapOf("x" to 2.0))) as Double) shouldBe (0.5 plusOrMinus 1e-9)
            }
        }
    }

    @Test
    fun `differentiation by x leaves a second variable alone`() {
        val xyDouble = mapOf<String, KClass<*>>("x" to Double::class, "y" to Double::class)
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val d = derivative("\$x * \$y", backend, xyDouble)
                (d.eval(MapEvaluationContext(mapOf("x" to 2.0, "y" to 3.0))) as Double) shouldBe
                    (3.0 plusOrMinus 1e-9)
            }
        }
    }

    @Test
    fun `specialized compile of a derivative via AsmBackend`() {
        val tree = Differentiator.differentiate(compiler.parseTree("sin(\$x)", xDouble), "x")
        val d = AsmBackend.compile(tree, D1::class) as D1
        d.eval(0.0) shouldBe 1.0
    }

    @Test
    fun `differentiating an if expression fails with a clear error`() {
        val logicCompiler = ExpressionCompiler(MathSyntax, LogicExtension)
        shouldThrow<ExpressionException> {
            Differentiator.differentiate(logicCompiler.parseTree("if(true, 1.0, 2.0)"), "x")
        }
    }

    @Test
    fun `differentiating abs fails with a clear error`() {
        shouldThrow<ExpressionException> {
            Differentiator.differentiate(compiler.parseTree("abs(\$x)", xDouble), "x")
        }
    }

    @Test
    fun `differentiating a string expression fails with a clear error`() {
        val stringCompiler = ExpressionCompiler(MathSyntax, StringExtension)
        shouldThrow<ExpressionException> {
            Differentiator.differentiate(stringCompiler.parseTree("\"ab\" + \"cd\""), "x")
        }
    }

    @Test
    fun `a custom differentiable extension plugs in without core or calculus changes`() {
        val cubeCompiler = ExpressionCompiler(MathSyntax, CubeExtension)
        val tree = Differentiator.differentiate(cubeCompiler.parseTree("cube(\$x)", xDouble), "x")
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val d = cubeCompiler.compileTree(tree, backend)
                // d/dx x³ = 3x²
                (d.eval(MapEvaluationContext(mapOf("x" to 2.0))) as Double) shouldBe (12.0 plusOrMinus 1e-9)
                (d.eval(MapEvaluationContext(mapOf("x" to -1.5))) as Double) shouldBe (6.75 plusOrMinus 1e-9)
            }
        }
    }

    @Test
    fun `simplify leaves unknown node classes unchanged`() {
        val node = CubeNode(VariableNode("x", Double::class))
        Differentiator.simplify(node) shouldBe node
    }
}
