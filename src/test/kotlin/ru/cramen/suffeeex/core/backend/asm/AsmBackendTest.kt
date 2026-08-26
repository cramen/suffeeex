package ru.cramen.suffeeex.core.backend.asm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.ext.math.MathSyntax
import kotlin.reflect.KClass
import kotlin.test.Test

internal class AsmBackendTest {

    private val compiler = ExpressionCompiler(MathSyntax)

    // shouldBe against a typed literal asserts both the value and the runtime
    // type (e.g. Int 14 is not equal to Long 14).
    private fun eval(
        source: String,
        varTypes: Map<String, KClass<*>> = emptyMap(),
        context: Map<String, Any?> = emptyMap(),
    ): Any? = compiler.compile(source, varTypes, AsmBackend).eval(MapEvaluationContext(context))

    @Test
    fun `int arithmetic stays int`() {
        eval("2 + 3 * 4") shouldBe 14
    }

    @Test
    fun `long arithmetic stays long`() {
        eval("2L * 3L") shouldBe 6L
    }

    @Test
    fun `double math function stays double`() {
        eval("sqrt(16.0)") shouldBe 4.0
    }

    @Test
    fun `float arithmetic stays float`() {
        eval("1.5f + 1.5f") shouldBe 3.0f
    }

    @Test
    fun `resolves variable from context`() {
        eval("\$x * 2", mapOf("x" to Int::class), mapOf("x" to 21)) shouldBe 42
    }

    @Test
    fun `numeric conversion composes with arithmetic`() {
        eval("toLong(1) + 2L") shouldBe 3L
    }

    @Test
    fun `mixed-type operands are a compile error`() {
        shouldThrow<ExpressionException> { compiler.compile("1 + 1L", backend = AsmBackend) }
    }
}
