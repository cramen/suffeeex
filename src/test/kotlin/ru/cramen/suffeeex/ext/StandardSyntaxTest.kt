package ru.cramen.suffeeex.ext

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import kotlin.reflect.KClass
import kotlin.test.Test

internal class StandardSyntaxTest {

    private val compiler = ExpressionCompiler(StandardSyntax)

    private fun eval(
        source: String,
        varTypes: Map<String, KClass<*>> = emptyMap(),
        context: Map<String, Any?> = emptyMap(),
        backend: ExpressionBackend,
    ): Any? = compiler.compile(source, varTypes, backend).eval(MapEvaluationContext(context))

    @Test
    fun `if picks string branch by comparison`() {
        for ((_, backend) in ALL_BACKENDS) {
            val expression = compiler.compile(
                "if(\$x > 5, \"big\", \"small\")",
                mapOf("x" to Int::class),
                backend,
            )
            expression.eval(MapEvaluationContext(mapOf("x" to 10))) shouldBe "big"
            expression.eval(MapEvaluationContext(mapOf("x" to 3))) shouldBe "small"
        }
    }

    @Test
    fun `string function result in arithmetic`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval(
                "length(\$s) + 1",
                mapOf("s" to String::class),
                mapOf("s" to "abcd"),
                backend,
            ) shouldBe 5
        }
    }

    @Test
    fun `comparisons combine with logical operators`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval(
                "\$a > 1 && \$b < 10 || false",
                mapOf("a" to Int::class, "b" to Int::class),
                mapOf("a" to 5, "b" to 3),
                backend,
            ) shouldBe true
        }
    }

    @Test
    fun `if with string predicate and branches of mixed origin`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval(
                "if(contains(\$s, \"x\"), length(\$s), -1)",
                mapOf("s" to String::class),
                mapOf("s" to "xyz"),
                backend,
            ) shouldBe 3
        }
    }

    @Test
    fun `comparison binds tighter than equality`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval(
                "max(\$a, 3) >= 3 == true",
                mapOf("a" to Int::class),
                mapOf("a" to 1),
                backend,
            ) shouldBe true
        }
    }

    @Test
    fun `comparing number with string is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("1 < \"a\"", backend = backend) }
        }
    }

    @Test
    fun `string ordering comparison is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("\"a\" < \"b\"", backend = backend) }
        }
    }

    @Test
    fun `if with non-boolean condition is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> {
                eval("if(\$x, 1, 2)", mapOf("x" to Int::class), backend = backend)
            }
        }
    }

    @Test
    fun `if with mismatched branch types is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("if(true, 1, \"a\")", backend = backend) }
        }
    }
}
