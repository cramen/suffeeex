package ru.cramen.suffeeex.ext.math.function

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.ext.math.bracket.BracketExtension
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import ru.cramen.suffeeex.ext.math.operator.ArithmeticExtension
import kotlin.test.Test

internal class MathFunctionsExtensionTest {

    private val compiler = ExpressionCompiler(NumberExtension, BracketExtension, MathFunctionsExtension)

    // negative literals need the prefix minus operator from ArithmeticExtension
    private val compilerWithArithmetic =
        ExpressionCompiler(NumberExtension, BracketExtension, ArithmeticExtension, MathFunctionsExtension)

    private fun eval(source: String, backend: ExpressionBackend): Any? =
        compiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    private fun evalWithArithmetic(source: String, backend: ExpressionBackend): Any? =
        compilerWithArithmetic.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    @Test
    fun `sqrt of double returns double`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("sqrt(16.0)", backend) shouldBe 4.0
        }
    }

    @Test
    fun `sqrt of int literal is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("sqrt(16)", backend) }
        }
    }

    @Test
    fun `abs preserves int`() {
        for ((_, backend) in ALL_BACKENDS) {
            evalWithArithmetic("abs(-5)", backend) shouldBe 5
        }
    }

    @Test
    fun `abs preserves long`() {
        for ((_, backend) in ALL_BACKENDS) {
            evalWithArithmetic("abs(-5L)", backend) shouldBe 5L
        }
    }

    @Test
    fun `abs on double returns double`() {
        for ((_, backend) in ALL_BACKENDS) {
            evalWithArithmetic("abs(-5.5)", backend) shouldBe 5.5
        }
    }

    @Test
    fun `max of two ints returns int`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("max(1, 2)", backend) shouldBe 2
        }
    }

    @Test
    fun `min of two doubles returns double`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("min(1.5, 2.0)", backend) shouldBe 1.5
        }
    }

    @Test
    fun `min of mixed types is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("min(1.5, 2)", backend) }
        }
    }

    @Test
    fun `pow of doubles returns double`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("pow(2.0, 10.0)", backend) shouldBe 1024.0
        }
    }

    @Test
    fun `pow of int literals is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("pow(2, 10)", backend) }
        }
    }

    @Test
    fun `trig and log functions return double`() {
        for ((_, backend) in ALL_BACKENDS) {
            (eval("sin(0.0)", backend) as Double) shouldBe (0.0 plusOrMinus 1e-9)
            (eval("cos(0.0)", backend) as Double) shouldBe (1.0 plusOrMinus 1e-9)
            (eval("tan(0.0)", backend) as Double) shouldBe (0.0 plusOrMinus 1e-9)
            (eval("ln(1.0)", backend) as Double) shouldBe (0.0 plusOrMinus 1e-9)
            (eval("log10(100.0)", backend) as Double) shouldBe (2.0 plusOrMinus 1e-9)
            (eval("exp(1.0)", backend) as Double) shouldBe (2.718281828459045 plusOrMinus 1e-9)
        }
    }

    @Test
    fun `rounding functions return double`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("floor(2.7)", backend) shouldBe 2.0
            eval("ceil(2.1)", backend) shouldBe 3.0
            eval("round(2.5)", backend) shouldBe 2.0 // kotlin.math.round: ties to even (Math.rint in ASM)
        }
    }

    @Test
    fun `nested calls`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("sqrt(pow(3.0, 2.0))", backend) shouldBe 3.0
        }
    }

    @Test
    fun `conversion functions change the static type`() {
        for ((_, backend) in ALL_BACKENDS) {
            evalWithArithmetic("toLong(1) + 2L", backend) shouldBe 3L
            evalWithArithmetic("toDouble(5) / toDouble(2)", backend) shouldBe 2.5
            evalWithArithmetic("toFloat(1) + 0.5f", backend) shouldBe 1.5f
            eval("toInt(2.7)", backend) shouldBe 2
        }
    }

    @Test
    fun `throws on unknown function`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("foo(1)", backend) }
        }
    }

    @Test
    fun `throws on wrong arity`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("sqrt(1.0, 2.0)", backend) }
        }
    }
}
