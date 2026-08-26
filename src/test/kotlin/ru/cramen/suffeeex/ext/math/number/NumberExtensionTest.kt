package ru.cramen.suffeeex.ext.math.number

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import kotlin.test.Test

internal class NumberExtensionTest {

    private val compiler = ExpressionCompiler(NumberExtension)
    private val context = MapEvaluationContext(emptyMap())

    @Test
    fun `integer literal is Int`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("123", backend = backend).eval(context) shouldBe 123
            }
        }
    }

    @Test
    fun `evaluates zero`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("0", backend = backend).eval(context) shouldBe 0
            }
        }
    }

    @Test
    fun `integer literal overflowing Int is Long`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("3000000000", backend = backend).eval(context) shouldBe 3000000000L
            }
        }
    }

    @Test
    fun `L suffix makes a Long literal`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("123L", backend = backend).eval(context) shouldBe 123L
                compiler.compile("123l", backend = backend).eval(context) shouldBe 123L
            }
        }
    }

    @Test
    fun `floating point literal is Double`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("1.5", backend = backend).eval(context) shouldBe 1.5
            }
        }
    }

    @Test
    fun `f suffix makes a Float literal`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("1.5f", backend = backend).eval(context) shouldBe 1.5f
                compiler.compile("123F", backend = backend).eval(context) shouldBe 123.0f
            }
        }
    }

    @Test
    fun `L suffix on a floating point literal is a compile error`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                shouldThrow<ExpressionException> { compiler.compile("1.5L", backend = backend) }
            }
        }
    }

    @Test
    fun `ignores surrounding whitespace`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("  42  ", backend = backend).eval(context) shouldBe 42
            }
        }
    }
}
