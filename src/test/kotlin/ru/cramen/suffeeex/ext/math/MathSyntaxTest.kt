package ru.cramen.suffeeex.ext.math

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import kotlin.reflect.KClass
import kotlin.test.Test

internal class MathSyntaxTest {

    private val compiler = ExpressionCompiler(MathSyntax)

    private fun eval(
        source: String,
        varTypes: Map<String, KClass<*>> = emptyMap(),
        context: Map<String, Any?> = emptyMap(),
        backend: ExpressionBackend,
    ): Any? = compiler.compile(source, varTypes, backend).eval(MapEvaluationContext(context))

    @Test
    fun `arithmetic with precedence`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("2 + 3 * 4 - 1", backend = backend) shouldBe 13
        }
    }

    @Test
    fun `unary minus with grouped expression`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("-(2 + 3) * 4", backend = backend) shouldBe -20
        }
    }

    @Test
    fun `nested function calls`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("sqrt(pow(3.0, 2.0) + pow(4.0, 2.0))", backend = backend) shouldBe (5.0 plusOrMinus 1e-9)
        }
    }

    @Test
    fun `function over variable and literal`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("max(\$a, 10) * 2", mapOf("a" to Int::class), mapOf("a" to 7), backend) shouldBe 20
        }
    }

    @Test
    fun `abs of variable expression`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("abs(\$x - 5)", mapOf("x" to Int::class), mapOf("x" to 2), backend) shouldBe 3
        }
    }

    @Test
    fun `conversions combine ints and doubles with variables`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval(
                "toDouble(\$rate) * 1.5 + floor(\$bonus)",
                mapOf("rate" to Int::class, "bonus" to Double::class),
                mapOf("rate" to 10, "bonus" to 2.7),
                backend,
            ) shouldBe (17.0 plusOrMinus 1e-9)
        }
    }
}
