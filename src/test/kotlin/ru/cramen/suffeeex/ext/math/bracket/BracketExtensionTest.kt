package ru.cramen.suffeeex.ext.math.bracket

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import ru.cramen.suffeeex.ext.math.operator.ArithmeticExtension
import kotlin.test.Test

internal class BracketExtensionTest {

    private val compiler = ExpressionCompiler(NumberExtension, ArithmeticExtension, BracketExtension)

    private fun eval(source: String, backend: ExpressionBackend): Any? =
        compiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    @Test
    fun `brackets override precedence`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("(2+3)*4", backend) shouldBe 20
        }
    }

    @Test
    fun `brackets group right operand`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("2*(3+4)", backend) shouldBe 14
        }
    }

    @Test
    fun `nested redundant brackets`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("((1+2))", backend) shouldBe 3
        }
    }

    @Test
    fun `throws on missing close bracket`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("(1+2", backend) }
        }
    }
}
