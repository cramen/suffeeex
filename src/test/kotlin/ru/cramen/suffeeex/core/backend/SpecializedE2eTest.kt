package ru.cramen.suffeeex.core.backend

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.ext.logic.LogicExtension
import ru.cramen.suffeeex.ext.math.MathSyntax
import ru.cramen.suffeeex.ext.string.StringExtension
import kotlin.test.Test

/**
 * End-to-end specialized compilation through the real [MathSyntax]
 * extensions (variables, arithmetic, brackets, math functions), on every
 * backend. Complements [SpecializedCompileTest], which focuses on signature
 * validation and plain variable loads.
 */
internal class SpecializedE2eTest {

    fun interface Calc {
        fun eval(a: Long, b: Long): Long
    }

    fun interface Hypot {
        fun eval(a: Double, b: Double): Double
    }

    fun interface ShiftedAbs {
        fun eval(x: Long): Long
    }

    fun interface IsPositive {
        fun eval(a: Int): Boolean
    }

    fun interface Shout {
        fun eval(s: String): String
    }

    private val compiler = ExpressionCompiler(MathSyntax)
    private val logicCompiler = ExpressionCompiler(MathSyntax, LogicExtension)
    private val stringCompiler = ExpressionCompiler(MathSyntax, StringExtension)

    @Test
    fun `long arithmetic with precedence on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val calc = compiler.compile("2L + 3L * \$a - 10L / \$b", Calc::class, backend)
                calc.eval(5L, 3L) shouldBe 14L // 2 + 15 - 3
                calc.eval(10L, 4L) shouldBe 30L // 2 + 30 - 2
                calc.eval(-7L, 2L) shouldBe -24L // 2 - 21 - 5
            }
        }
    }

    @Test
    fun `double math functions on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val hypot = compiler.compile("sqrt(pow(\$a, 2.0) + pow(\$b, 2.0))", Hypot::class, backend)
                hypot.eval(3.0, 4.0) shouldBe 5.0
                hypot.eval(6.0, 8.0) shouldBe 10.0
                hypot.eval(0.0, 0.0) shouldBe 0.0
            }
        }
    }

    @Test
    fun `typed abs stays long on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = compiler.compile("abs(\$x - 5L) * 2L", ShiftedAbs::class, backend)
                op.eval(7L) shouldBe 4L
                op.eval(3L) shouldBe 4L
                op.eval(5L) shouldBe 0L
            }
        }
    }

    @Test
    fun `boolean comparison result on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = logicCompiler.compile("\$a > 0", IsPositive::class, backend)
                op.eval(1) shouldBe true
                op.eval(0) shouldBe false
                op.eval(-5) shouldBe false
            }
        }
    }

    @Test
    fun `string concat on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val op = stringCompiler.compile("\$s + \"!\"", Shout::class, backend)
                op.eval("wow") shouldBe "wow!"
                op.eval("") shouldBe "!"
            }
        }
    }
}
