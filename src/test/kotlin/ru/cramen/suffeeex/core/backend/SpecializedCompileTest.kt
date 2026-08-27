package ru.cramen.suffeeex.core.backend

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.TypeEmissions
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.ext.math.MathSyntax
import kotlin.test.Test

internal class SpecializedCompileTest {

    fun interface IntBinOp {
        fun apply(a: Int, b: Int): Int
    }

    fun interface LongBinOp {
        fun apply(a: Long, b: Long): Long
    }

    fun interface Reordered {
        fun apply(x: Int, y: Int): Int
    }

    fun interface Reused {
        fun apply(a: Int): Int
    }

    fun interface WrapperReturn {
        fun apply(a: Int): java.lang.Integer
    }

    fun interface NullableReturn {
        fun apply(a: Int): Int?
    }

    fun interface MixedSlots {
        fun apply(a: Int, b: Long, c: Double): Double
    }

    fun interface LongReturn {
        fun apply(a: Int): Long
    }

    fun interface FlagOp {
        fun apply(flag: Boolean): Boolean
    }

    fun interface Echo {
        fun apply(s: String): String
    }

    private val compiler = ExpressionCompiler(MathSyntax)

    @Test
    fun `compiles int parameters on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = compiler.compile("\$a + \$b * 2", IntBinOp::class, backend)
                op.apply(1, 2) shouldBe 5
                op.apply(10, -3) shouldBe 4
            }
        }
    }

    @Test
    fun `compiles long parameters on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = compiler.compile("\$a * \$b", LongBinOp::class, backend)
                op.apply(6L, 7L) shouldBe 42L
            }
        }
    }

    @Test
    fun `parameter order is independent from occurrence order in source`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = compiler.compile("\$y - \$x", Reordered::class, backend)
                op.apply(1, 10) shouldBe 9
            }
        }
    }

    @Test
    fun `one parameter can be used twice`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = compiler.compile("\$a * \$a + \$a", Reused::class, backend)
                op.apply(3) shouldBe 12
            }
        }
    }

    @Test
    fun `wrapper return type boxes the primitive result`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = compiler.compile("\$a * 2", WrapperReturn::class, backend)
                op.apply(21) shouldBe java.lang.Integer(42)
            }
        }
    }

    @Test
    fun `nullable return type boxes the primitive result`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = compiler.compile("\$a * 2", NullableReturn::class, backend)
                op.apply(21) shouldBe 42
            }
        }
    }

    @Test
    fun `mixed parameter types occupy the right local slots`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = compiler.compile("toDouble(\$a) + toDouble(\$b) + \$c", MixedSlots::class, backend)
                op.apply(1, 2L, 0.5) shouldBe 3.5
            }
        }
    }

    @Test
    fun `default backend is asm`() {
        val op = compiler.compile("\$a + 1", Reused::class)
        op.apply(41) shouldBe 42
    }

    @Test
    fun `boolean parameter and return`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = compiler.compile("\$flag", FlagOp::class, backend)
                op.apply(true) shouldBe true
            }
        }
    }

    @Test
    fun `string parameter and return`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = compiler.compile("\$s", Echo::class, backend)
                op.apply("hello") shouldBe "hello"
            }
        }
    }

    @Test
    fun `extra parameter is an error`() {
        val exception = shouldThrow<ExpressionException> {
            compiler.compile("\$a + 1", IntBinOp::class)
        }
        exception.message shouldContain "not used"
    }

    @Test
    fun `missing parameter is an error`() {
        val exception = shouldThrow<ExpressionException> {
            compiler.compile("\$a + \$b", Reused::class)
        }
        exception.message shouldContain "b"
    }

    @Test
    fun `wrong return type is an error`() {
        val exception = shouldThrow<ExpressionException> {
            compiler.compile("\$a + 1", LongReturn::class)
        }
        exception.message shouldContain "return type mismatch"
    }

    @Test
    fun `non-interface target is an error`() {
        class NotAnInterface
        val exception = shouldThrow<ExpressionException> {
            compiler.compile("\$a + 1", NotAnInterface::class)
        }
        exception.message shouldContain "interface"
    }

    @Test
    fun `backend without specialized support is an error`() {
        val plainBackend = object : ExpressionBackend {
            override fun compile(root: TypedNode, types: TypeEmissions): Expression = root.build()
        }
        val exception = shouldThrow<ExpressionException> {
            compiler.compile("\$a + \$b", IntBinOp::class, plainBackend)
        }
        exception.message shouldContain "does not support specialized compilation"
    }
}
