package ru.cramen.suffeeex.ext.host

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.ext.math.bracket.BracketExtension
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import ru.cramen.suffeeex.ext.math.operator.ArithmeticExtension
import kotlin.test.Test

fun calcVat(amount: Double, rate: Double): Double = amount * rate

fun twice(x: Int): Int = x * 2

fun label(x: Int): String = "n$x"

object Rounding {
    fun half(x: Double): Double = x / 2.0
}

class VatRules {
    companion object {
        fun thrice(x: Int): Int = x * 3
    }
}

class TaxCalculator {
    @Suppress("unused")
    fun calc(x: Double): Double = x
}

internal class HostFunctionsTest {

    private val compiler = ExpressionCompiler(
        NumberExtension,
        ArithmeticExtension,
        BracketExtension,
        HostFunctionsExtension(
            "vat" to ::calcVat,
            "twice" to ::twice,
            "label" to ::label,
            "half" to Rounding::half,
            "thrice" to VatRules.Companion::thrice,
        ),
    )

    private fun eval(source: String, backend: ExpressionBackend): Any? =
        compiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    @Test
    fun `top-level function returns correct result`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("vat(100.0, 0.2)", backend) shouldBe 20.0
        }
    }

    @Test
    fun `int function keeps int type`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("twice(21)", backend) shouldBe 42
        }
    }

    @Test
    fun `string-returning function works`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("label(7)", backend) shouldBe "n7"
        }
    }

    @Test
    fun `object member function works`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("half(9.0)", backend) shouldBe 4.5
        }
    }

    @Test
    fun `companion object member function works`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("thrice(7)", backend) shouldBe 21
        }
    }

    @Test
    fun `host functions compose with arithmetic`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("vat(100.0, 0.2) + half(3.0)", backend) shouldBe 21.5
        }
    }

    @Test
    fun `wrong argument type is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            val e = shouldThrow<ExpressionException> { eval("vat(1, 0.2)", backend) }
            e.message shouldContain "Double"
        }
    }

    @Test
    fun `wrong arity is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("vat(1.0)", backend) }
            shouldThrow<ExpressionException> { eval("twice(1, 2)", backend) }
        }
    }

    @Test
    fun `instance method is rejected with a clear error`() {
        val e = shouldThrow<ExpressionException> {
            ExpressionCompiler(
                NumberExtension,
                ArithmeticExtension,
                BracketExtension,
                HostFunctionsExtension("calc" to TaxCalculator::calc),
            )
        }
        e.message shouldContain "static/object only"
    }

    @Test
    fun `registerHostFunction registers directly into a registry`() {
        val direct = ExpressionCompiler(
            NumberExtension,
            ArithmeticExtension,
            BracketExtension,
            // identifier token + function name token type come from HostFunctionsExtension
            HostFunctionsExtension(),
            SyntaxExtension { registry -> registry.registerHostFunction("twice", ::twice) },
        )
        for ((_, backend) in ALL_BACKENDS) {
            direct.compile("twice(5)", backend = backend).eval(MapEvaluationContext(emptyMap())) shouldBe 10
        }
    }
}
