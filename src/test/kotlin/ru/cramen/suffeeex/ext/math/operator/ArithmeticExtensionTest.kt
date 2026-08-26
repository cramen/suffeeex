package ru.cramen.suffeeex.ext.math.operator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import kotlin.test.Test

internal class ArithmeticExtensionTest {

    private val compiler = ExpressionCompiler(NumberExtension, ArithmeticExtension)

    private fun eval(source: String, backend: ExpressionBackend): Any? =
        compiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

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
}
