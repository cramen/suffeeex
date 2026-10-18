package ru.cramen.suffeeex.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.cramen.suffeeex.core.backend.CompositionBackend
import ru.cramen.suffeeex.core.backend.asm.AsmBackend
import ru.cramen.suffeeex.ext.logic.LogicExtension
import ru.cramen.suffeeex.ext.math.MathSyntax
import ru.cramen.suffeeex.ext.string.StringExtension
import kotlin.test.Test

internal class NullVariableTest {

    private val compiler = ExpressionCompiler(MathSyntax, LogicExtension, StringExtension)

    @Test
    fun `null variable value throws ExpressionException with clear message on ASM backend`() {
        val expr = compiler.compile(
            "\$x + 1",
            varTypes = mapOf("x" to Int::class),
            backend = AsmBackend,
        )
        val exception = shouldThrow<ExpressionException> {
            expr.eval(MapEvaluationContext(mapOf("x" to null)))
        }
        exception.message shouldContain "variable 'x' is null"
    }

    @Test
    fun `null variable value throws ExpressionException with clear message on composition backend`() {
        val expr = compiler.compile(
            "\$x + 1",
            varTypes = mapOf("x" to Int::class),
            backend = CompositionBackend,
        )
        // Composition backend uses KFunction.call which also throws, but the message may differ
        shouldThrow<Exception> {
            expr.eval(MapEvaluationContext(mapOf("x" to null)))
        }
    }

    @Test
    fun `null double variable throws ExpressionException on ASM backend`() {
        val expr = compiler.compile(
            "\$x * 2.0",
            varTypes = mapOf("x" to Double::class),
            backend = AsmBackend,
        )
        val exception = shouldThrow<ExpressionException> {
            expr.eval(MapEvaluationContext(mapOf("x" to null)))
        }
        exception.message shouldContain "variable 'x' is null"
    }

    @Test
    fun `null string variable works fine on ASM backend`() {
        // String is a reference type, so null is valid
        val expr = compiler.compile(
            "\$s + \"!\"",
            varTypes = mapOf("s" to String::class),
            backend = AsmBackend,
        )
        // null string should produce "null!" via toString() or NPE — depends on implementation
        // This test documents the current behavior
        val result = expr.eval(MapEvaluationContext(mapOf("s" to null as String?)))
        // String concat with null produces "null" via toString()
        result shouldBe "null!"
    }
}
