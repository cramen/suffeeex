package ru.cramen.suffeeex.ext.decimal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.ext.StandardSyntax
import ru.cramen.suffeeex.ext.logic.LogicExtension
import ru.cramen.suffeeex.ext.math.bracket.BracketExtension
import ru.cramen.suffeeex.ext.math.function.MathFunctionsExtension
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import ru.cramen.suffeeex.ext.math.operator.ArithmeticExtension
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test

internal class DecimalExtensionTest {

    private val compiler = ExpressionCompiler(
        NumberExtension, ArithmeticExtension, BracketExtension, LogicExtension, DecimalExtension()
    )

    // Full standard syntax: math functions, strings, variables and decimals,
    // so the decimal function parsers delegate to the math ones.
    private val stdCompiler = ExpressionCompiler(StandardSyntax)

    private val halfUpCompiler = ExpressionCompiler(
        NumberExtension, ArithmeticExtension, BracketExtension, MathFunctionsExtension,
        LogicExtension, DecimalExtension(RoundingMode.HALF_UP)
    )

    private fun eval(source: String, backend: ExpressionBackend): Any? =
        compiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    private fun stdEval(source: String, backend: ExpressionBackend): Any? =
        stdCompiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    private fun halfUpEval(source: String, backend: ExpressionBackend): Any? =
        halfUpCompiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    @Test
    fun `bigdecimal literals`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1.5bd", backend) shouldBe BigDecimal("1.5")
            eval("42bd", backend) shouldBe BigDecimal("42")
        }
    }

    @Test
    fun `bigdecimal arithmetic`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1.5bd + 2.25bd", backend) shouldBe BigDecimal("1.5").add(BigDecimal("2.25"))
            eval("5bd - 2.5bd", backend) shouldBe BigDecimal("5").subtract(BigDecimal("2.5"))
            eval("1.5bd * 2bd", backend) shouldBe BigDecimal("1.5").multiply(BigDecimal("2"))
            eval("5bd / 2bd", backend) shouldBe BigDecimal("5").divide(BigDecimal("2"))
            eval("5bd % 2bd", backend) shouldBe BigDecimal("5").remainder(BigDecimal("2"))
        }
    }

    @Test
    fun `raw bigdecimal semantics - division by zero throws arithmetic exception`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ArithmeticException> {
                eval("1bd / 0bd", backend)
            }
        }
    }

    @Test
    fun `unary minus`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("-1.5bd", backend) shouldBe BigDecimal("-1.5")
            // numeric operands still take the arithmetic negation
            eval("-3 + 1", backend) shouldBe -2
        }
    }

    @Test
    fun `precedence and parentheses`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1bd + 2bd * 3bd", backend) shouldBe BigDecimal("7")
            eval("(1.5bd + 2.5bd) * 2bd", backend) shouldBe
                BigDecimal("1.5").add(BigDecimal("2.5")).multiply(BigDecimal("2"))
        }
    }

    @Test
    fun `bigdecimal comparisons`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1.5bd < 2bd", backend) shouldBe true
            eval("2bd <= 2bd", backend) shouldBe true
            eval("3bd > 2.5bd", backend) shouldBe true
            eval("2bd >= 3bd", backend) shouldBe false
            eval("2bd == 2bd", backend) shouldBe true
            eval("2bd != 2bd", backend) shouldBe false
        }
    }

    @Test
    fun `equality follows compareTo and ignores scale`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1.0bd == 1.00bd", backend) shouldBe true
            eval("1.0bd != 1.00bd", backend) shouldBe false
        }
    }

    @Test
    fun `bigdecimal branches of if`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("if(1.5bd < 2bd, 10bd, 20bd)", backend) shouldBe BigDecimal("10")
            eval("if(1.5bd > 2bd, 10bd, 20bd)", backend) shouldBe BigDecimal("20")
        }
    }

    @Test
    fun `numeric expressions are unaffected`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("2+3*4", backend) shouldBe 14
            eval("1.5 < 2.5", backend) shouldBe true
        }
    }

    @Test
    fun `toBigDecimal conversions`() {
        for ((_, backend) in ALL_BACKENDS) {
            stdEval("toBigDecimal(2)", backend) shouldBe BigDecimal("2")
            // Int literal overflowing to Long
            stdEval("toBigDecimal(3000000000)", backend) shouldBe BigDecimal("3000000000")
            // string-exact Double semantics: valueOf, not the IEEE-754 expansion
            stdEval("toBigDecimal(0.1)", backend) shouldBe BigDecimal("0.1")
            stdEval("""toBigDecimal("1.25")""", backend) shouldBe BigDecimal("1.25")
        }
    }

    @Test
    fun `toBigDecimal enables mixed expressions`() {
        for ((_, backend) in ALL_BACKENDS) {
            stdEval("1bd + toBigDecimal(2)", backend) shouldBe BigDecimal("3")
            stdCompiler.compile("toBigDecimal(\$n)", mapOf("n" to Int::class), backend)
                .eval(MapEvaluationContext(mapOf("n" to 7))) shouldBe BigDecimal("7")
        }
    }

    @Test
    fun `toBigDecimal rejects other types at compile time`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> {
                stdCompiler.compile("toBigDecimal(true)", backend = backend)
            }
        }
    }

    @Test
    fun `bigdecimal to primitive conversions truncate`() {
        for ((_, backend) in ALL_BACKENDS) {
            stdEval("toInt(1.9bd)", backend) shouldBe 1
            stdEval("toLong(1.9bd)", backend) shouldBe 1L
            stdEval("toFloat(1.5bd)", backend) shouldBe 1.5f
            stdEval("toDouble(1.5bd)", backend) shouldBe 1.5
        }
    }

    @Test
    fun `numeric conversions still work in the same compiler`() {
        for ((_, backend) in ALL_BACKENDS) {
            stdEval("toInt(2.7)", backend) shouldBe 2
            stdEval("toLong(3)", backend) shouldBe 3L
            stdEval("toDouble(3)", backend) shouldBe 3.0
        }
    }

    @Test
    fun `bigdecimal abs min max`() {
        for ((_, backend) in ALL_BACKENDS) {
            stdEval("abs(0bd - 1.5bd)", backend) shouldBe BigDecimal("1.5")
            stdEval("min(1.5bd, 2bd)", backend) shouldBe BigDecimal("1.5")
            stdEval("max(1.5bd, 2bd)", backend) shouldBe BigDecimal("2")
        }
    }

    @Test
    fun `numeric abs min max still work in the same compiler`() {
        for ((_, backend) in ALL_BACKENDS) {
            stdEval("abs(-3)", backend) shouldBe 3
            stdEval("min(1, 2)", backend) shouldBe 1
            stdEval("max(1.5, 2.5)", backend) shouldBe 2.5
        }
    }

    @Test
    fun `bigdecimal pow and signum`() {
        for ((_, backend) in ALL_BACKENDS) {
            stdEval("pow(2bd, 10)", backend) shouldBe BigDecimal("1024")
            stdEval("signum(0bd - 1.5bd)", backend) shouldBe -1
            stdEval("signum(0bd)", backend) shouldBe 0
            stdEval("signum(2bd)", backend) shouldBe 1
            // Double pow still takes the math parser
            stdEval("pow(2.0, 10.0)", backend) shouldBe 1024.0
        }
    }

    @Test
    fun `half up rounding applies to division and setScale`() {
        for ((_, backend) in ALL_BACKENDS) {
            halfUpEval("1.00bd / 3bd", backend) shouldBe BigDecimal("0.33")
            halfUpEval("setScale(1.005bd, 2)", backend) shouldBe BigDecimal("1.01")
        }
    }

    @Test
    fun `default unnecessary rounding keeps raw semantics`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ArithmeticException> {
                eval("1bd / 3bd", backend)
            }
            shouldThrow<ArithmeticException> {
                stdEval("setScale(1.005bd, 2)", backend)
            }
        }
    }
}
