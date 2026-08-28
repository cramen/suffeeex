package ru.cramen.suffeeex.ext.math.number

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.node.differentiateOrThrow
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

    @Test
    fun `differentiating a literal yields a zero Double literal`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                val tree = compiler.parseTree("1.5")
                val derivative = tree.differentiateOrThrow("x")
                derivative.type shouldBe Double::class
                compiler.compileTree(derivative, backend).eval(context) shouldBe 0.0
            }
        }
    }

    @Test
    fun `exponent literal is Double`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("1e3", backend = backend).eval(context) shouldBe 1000.0
                compiler.compile("1.5e-3", backend = backend).eval(context) shouldBe 0.0015
                compiler.compile("1E+3", backend = backend).eval(context) shouldBe 1000.0
            }
        }
    }

    @Test
    fun `exponent literal with f suffix is Float`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("1e3f", backend = backend).eval(context) shouldBe 1000.0f
                compiler.compile("1.5e-2F", backend = backend).eval(context) shouldBe 0.015f
            }
        }
    }

    @Test
    fun `L suffix on an exponent literal is a compile error`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                shouldThrow<ExpressionException> { compiler.compile("1e3L", backend = backend) }
                shouldThrow<ExpressionException> { compiler.compile("1e3l", backend = backend) }
            }
        }
    }

    @Test
    fun `underscores between digits are ignored`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("1_000_000", backend = backend).eval(context) shouldBe 1000000
                compiler.compile("1_000.5", backend = backend).eval(context) shouldBe 1000.5
                compiler.compile("1_0e2", backend = backend).eval(context) shouldBe 1000.0
                compiler.compile("1_000_000L", backend = backend).eval(context) shouldBe 1000000L
            }
        }
    }

    @Test
    fun `hex literal is Int`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("0xFF", backend = backend).eval(context) shouldBe 255
                compiler.compile("0Xff", backend = backend).eval(context) shouldBe 255
                compiler.compile("0xFF_EC", backend = backend).eval(context) shouldBe 65516
            }
        }
    }

    @Test
    fun `hex literal with L suffix is Long`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("0xFFL", backend = backend).eval(context) shouldBe 255L
            }
        }
    }

    @Test
    fun `hex literal overflowing Int is Long`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("0xFFFFFFFF", backend = backend).eval(context) shouldBe 4294967295L
            }
        }
    }

    @Test
    fun `hex literal beyond Long is a compile error`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                shouldThrow<ExpressionException> { compiler.compile("0x10000000000000000", backend = backend) }
            }
        }
    }

    @Test
    fun `binary literal is Int`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("0b101", backend = backend).eval(context) shouldBe 5
                compiler.compile("0B101", backend = backend).eval(context) shouldBe 5
            }
        }
    }

    @Test
    fun `binary literal with L suffix is Long`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("0b101L", backend = backend).eval(context) shouldBe 5L
            }
        }
    }

    @Test
    fun `binary literal overflowing Int is Long`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                compiler.compile("0b10000000000000000000000000000000", backend = backend).eval(context) shouldBe 2147483648L
            }
        }
    }

    @Test
    fun `malformed literals are compile errors`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue(name) {
                shouldThrow<ExpressionException> { compiler.compile("1__000", backend = backend) }
                shouldThrow<ExpressionException> { compiler.compile("0x", backend = backend) }
                shouldThrow<ExpressionException> { compiler.compile("1e", backend = backend) }
            }
        }
    }
}
