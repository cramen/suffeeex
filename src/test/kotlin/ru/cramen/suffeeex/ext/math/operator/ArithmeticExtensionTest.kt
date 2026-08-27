package ru.cramen.suffeeex.ext.math.operator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.core.node.differentiateOrThrow
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import ru.cramen.suffeeex.ext.variable.VariableExtension
import kotlin.test.Test

internal class ArithmeticExtensionTest {

    private val compiler = ExpressionCompiler(NumberExtension, ArithmeticExtension)
    private val varCompiler = ExpressionCompiler(NumberExtension, ArithmeticExtension, VariableExtension)

    private fun eval(source: String, backend: ExpressionBackend): Any? =
        compiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    private fun evalDerivative(source: String, x: Double, backend: ExpressionBackend): Any? {
        val derivative = varCompiler.parseTree(source, mapOf("x" to Double::class)).differentiateOrThrow("x")
        return varCompiler.compileTree(derivative, backend).eval(MapEvaluationContext(mapOf("x" to x)))
    }

    @Test
    fun `multiplication has higher precedence than addition`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("2+3*4", backend) shouldBe 14
        }
    }

    @Test
    fun `subtraction is left associative`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("10-2-3", backend) shouldBe 5
        }
    }

    @Test
    fun `int division truncates`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("5/2", backend) shouldBe 2
        }
    }

    @Test
    fun `long arithmetic stays long`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1L+2L", backend) shouldBe 3L
            eval("5L/2L", backend) shouldBe 2L
        }
    }

    @Test
    fun `float arithmetic stays float`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1.5f*2.0f", backend) shouldBe 3.0f
        }
    }

    @Test
    fun `double arithmetic stays double`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("5.0/2.0", backend) shouldBe 2.5
        }
    }

    @Test
    fun `remainder on ints`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("7%3", backend) shouldBe 1
        }
    }

    @Test
    fun `unary minus`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("-3+5", backend) shouldBe 2
            eval("-1.5f", backend) shouldBe -1.5f
        }
    }

    @Test
    fun `unary minus after infix operator`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("2*-3", backend) shouldBe -6
        }
    }

    @Test
    fun `mixed int and long operands are a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("1 + 1L", backend) }
        }
    }

    @Test
    fun `mixed int and double operands are a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("1 + 0.5", backend) }
        }
    }

    @Test
    fun `mixed float and double operands are a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("1.5f + 0.5", backend) }
        }
    }

    @Test
    fun `derivative of x squared is 2x`() {
        for ((_, backend) in ALL_BACKENDS) {
            evalDerivative("\$x*\$x", 3.0, backend) shouldBe 6.0
        }
    }

    @Test
    fun `derivative of linear expression is its slope`() {
        for ((_, backend) in ALL_BACKENDS) {
            evalDerivative("2.0*\$x + 1.0", 42.0, backend) shouldBe 2.0
        }
    }

    @Test
    fun `derivative of x cubed applies the product rule recursively`() {
        for ((_, backend) in ALL_BACKENDS) {
            evalDerivative("\$x*\$x*\$x", 2.0, backend) shouldBe 12.0
        }
    }

    @Test
    fun `derivative of one over x applies the quotient rule`() {
        for ((_, backend) in ALL_BACKENDS) {
            evalDerivative("1.0/\$x", 2.0, backend) shouldBe -0.25
        }
    }

    @Test
    fun `derivative of negation is negated derivative`() {
        for ((_, backend) in ALL_BACKENDS) {
            evalDerivative("-\$x", 5.0, backend) shouldBe -1.0
        }
    }

    @Test
    fun `differentiation of int expression is a compile error`() {
        shouldThrow<ExpressionException> {
            varCompiler.parseTree("\$x + 1", mapOf("x" to Int::class)).differentiateOrThrow("x")
        }
    }
}
