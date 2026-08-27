package ru.cramen.suffeeex.ext.variable

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.core.node.differentiateOrThrow
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import ru.cramen.suffeeex.ext.math.operator.ArithmeticExtension
import kotlin.reflect.KClass
import kotlin.test.Test

internal class VariableExtensionTest {

    private val compiler = ExpressionCompiler(NumberExtension, ArithmeticExtension, VariableExtension)

    private fun eval(
        source: String,
        varTypes: Map<String, KClass<*>>,
        context: Map<String, Any?>,
        backend: ExpressionBackend,
    ): Any? = compiler.compile(source, varTypes, backend).eval(MapEvaluationContext(context))

    @Test
    fun `resolves variable in arithmetic expression`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("\$x + 1", mapOf("x" to Int::class), mapOf("x" to 5), backend) shouldBe 6
        }
    }

    @Test
    fun `resolves multiple variables`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval(
                "\$x * \$y",
                mapOf("x" to Long::class, "y" to Long::class),
                mapOf("x" to 3L, "y" to 4L),
                backend,
            ) shouldBe 12L
        }
    }

    @Test
    fun `undeclared variable is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            val exception = shouldThrow<ExpressionException> { compiler.compile("\$z", backend = backend) }
            exception.message shouldContain "unknown variable: z"
        }
    }

    @Test
    fun `missing value in context is an eval error`() {
        for ((name, backend) in ALL_BACKENDS) {
            val expression = compiler.compile("\$x", mapOf("x" to Int::class), backend)
            if (name == "asm") {
                // ASM backend takes the minimal route: a missing value surfaces as a JVM
                // NPE during unboxing instead of an ExpressionException.
                shouldThrowAny { expression.eval(MapEvaluationContext(emptyMap())) }
            } else {
                shouldThrow<ExpressionException> { expression.eval(MapEvaluationContext(emptyMap())) }
            }
        }
    }

    @Test
    fun `value of wrong type in context is an eval error`() {
        for ((name, backend) in ALL_BACKENDS) {
            val expression = compiler.compile("\$x", mapOf("x" to Int::class), backend)
            if (name == "asm") {
                // ASM backend takes the minimal route: a wrong-typed value surfaces as a JVM
                // ClassCastException from the emitted checkcast instead of an ExpressionException.
                shouldThrowAny { expression.eval(MapEvaluationContext(mapOf("x" to "not a number"))) }
            } else {
                val exception = shouldThrow<ExpressionException> {
                    expression.eval(MapEvaluationContext(mapOf("x" to "not a number")))
                }
                exception.message shouldContain "must be Int"
            }
        }
    }

    @Test
    fun `supports digits and underscores in variable names`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("\$my_var2 + 1", mapOf("my_var2" to Int::class), mapOf("my_var2" to 1), backend) shouldBe 2
            eval(
                "\$_x1 + \$v2_",
                mapOf("_x1" to Int::class, "v2_" to Int::class),
                mapOf("_x1" to 2, "v2_" to 3),
                backend,
            ) shouldBe 5
        }
    }

    @Test
    fun `differentiating by own variable yields one`() {
        for ((_, backend) in ALL_BACKENDS) {
            val tree = compiler.parseTree("\$x", mapOf("x" to Double::class))
            val derivative = tree.differentiateOrThrow("x")
            derivative.type shouldBe Double::class
            compiler.compileTree(derivative, backend).eval(MapEvaluationContext(emptyMap())) shouldBe 1.0
        }
    }

    @Test
    fun `differentiating by another variable yields zero`() {
        for ((_, backend) in ALL_BACKENDS) {
            val tree = compiler.parseTree("\$y", mapOf("y" to Double::class))
            val derivative = tree.differentiateOrThrow("x")
            derivative.type shouldBe Double::class
            compiler.compileTree(derivative, backend).eval(MapEvaluationContext(emptyMap())) shouldBe 0.0
        }
    }

    @Test
    fun `differentiating a non-Double variable by itself is a compile error`() {
        val tree = compiler.parseTree("\$x", mapOf("x" to Int::class))
        val exception = shouldThrow<ExpressionException> { tree.differentiateOrThrow("x") }
        exception.message shouldContain "differentiation variable 'x' must be Double, got Int"
    }
}
