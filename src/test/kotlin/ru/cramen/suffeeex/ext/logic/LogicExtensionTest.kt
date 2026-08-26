package ru.cramen.suffeeex.ext.logic

import io.kotest.assertions.throwables.shouldThrow
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

internal class LogicExtensionTest {

    private val compiler =
        ExpressionCompiler(NumberExtension, ArithmeticExtension, BracketExtension, LogicExtension)

    private fun eval(source: String, backend: ExpressionBackend): Any? =
        compiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    @Test
    fun `boolean literals`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("true", backend) shouldBe true
            eval("false", backend) shouldBe false
        }
    }

    @Test
    fun `int comparisons`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1 < 2", backend) shouldBe true
            eval("2 <= 2", backend) shouldBe true
            eval("3 > 2", backend) shouldBe true
            eval("2 >= 3", backend) shouldBe false
            eval("2 == 2", backend) shouldBe true
            eval("2 != 2", backend) shouldBe false
        }
    }

    @Test
    fun `double comparisons`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1.5 < 2.5", backend) shouldBe true
            eval("2.5 <= 2.5", backend) shouldBe true
            eval("3.5 > 2.5", backend) shouldBe true
            eval("2.5 >= 3.5", backend) shouldBe false
            eval("2.5 == 2.5", backend) shouldBe true
            eval("2.5 != 2.5", backend) shouldBe false
        }
    }

    @Test
    fun `boolean equality`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("true == true", backend) shouldBe true
            eval("true == false", backend) shouldBe false
            eval("true != false", backend) shouldBe true
            eval("true != true", backend) shouldBe false
        }
    }

    @Test
    fun `logical not`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("!true", backend) shouldBe false
            eval("!false", backend) shouldBe true
            eval("!(1 < 2)", backend) shouldBe false
        }
    }

    @Test
    fun `operator precedence chain`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("1 + 2 < 4 && !false || false", backend) shouldBe true
            eval("1 + 2 * 3 == 7", backend) shouldBe true
            eval("!true == false", backend) shouldBe true
            eval("true || true && false", backend) shouldBe true
        }
    }

    @Test
    fun `if picks a branch by condition`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("if(1 < 2, 10, 20)", backend) shouldBe 10
            eval("if(1 > 2, 10, 20)", backend) shouldBe 20
            eval("if(true, true, false)", backend) shouldBe true
        }
    }

    @Test
    fun `logical and is lazy`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("false && (1 / 0 == 0)", backend) shouldBe false
        }
    }

    @Test
    fun `logical or is lazy`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("true || (1 / 0 == 0)", backend) shouldBe true
        }
    }

    @Test
    fun `if is lazy`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("if(true, 1, 1 / 0)", backend) shouldBe 1
            eval("if(false, 1 / 0, 2)", backend) shouldBe 2
        }
    }

    @Test
    fun `not on non-boolean is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("!1", backend) }
        }
    }

    @Test
    fun `comparison with mixed operand types is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("1 < 2L", backend) }
        }
    }

    @Test
    fun `equality with mixed operand types is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("1 == 2L", backend) }
            shouldThrow<ExpressionException> { eval("true == 1", backend) }
        }
    }

    @Test
    fun `logical operators on non-boolean operands are a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("1 && true", backend) }
            shouldThrow<ExpressionException> { eval("false || 1", backend) }
        }
    }

    @Test
    fun `if with non-boolean condition is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("if(1, 2, 3)", backend) }
        }
    }

    @Test
    fun `if with mismatched branch types is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("if(true, 1, 2L)", backend) }
        }
    }
}
