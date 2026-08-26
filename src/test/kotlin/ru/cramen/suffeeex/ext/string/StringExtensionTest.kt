package ru.cramen.suffeeex.ext.string

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.ext.logic.LogicExtension
import ru.cramen.suffeeex.ext.math.bracket.BracketExtension
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import ru.cramen.suffeeex.ext.math.operator.ArithmeticExtension
import ru.cramen.suffeeex.ext.variable.VariableExtension
import kotlin.test.Test

internal class StringExtensionTest {

    private val compiler = ExpressionCompiler(NumberExtension, ArithmeticExtension, BracketExtension, StringExtension)

    private val compilerWithLogic =
        ExpressionCompiler(NumberExtension, ArithmeticExtension, BracketExtension, LogicExtension, StringExtension)

    private val compilerWithVariables =
        ExpressionCompiler(NumberExtension, ArithmeticExtension, BracketExtension, VariableExtension, StringExtension)

    private fun eval(source: String, backend: ExpressionBackend): Any? =
        compiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    @Test
    fun `string literal`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("\"hello\"", backend) shouldBe "hello"
        }
    }

    @Test
    fun `escape sequences`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("\"a\\nb\"", backend) shouldBe "a\nb"
            eval("\"a\\tb\"", backend) shouldBe "a\tb"
            eval("\"a\\\"b\"", backend) shouldBe "a\"b"
            eval("\"a\\\\b\"", backend) shouldBe "a\\b"
        }
    }

    @Test
    fun `unknown escape is kept as-is`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("\"a\\qb\"", backend) shouldBe "a\\qb"
        }
    }

    @Test
    fun `string concatenation`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("\"a\" + \"b\"", backend) shouldBe "ab"
        }
    }

    @Test
    fun `chained concatenation`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("\"a\" + \"b\" + \"c\"", backend) shouldBe "abc"
        }
    }

    @Test
    fun `length of string`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("length(\"abc\")", backend) shouldBe 3
        }
    }

    @Test
    fun `contains substring`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("contains(\"hello\", \"ell\")", backend) shouldBe true
        }
    }

    @Test
    fun `does not contain substring`() {
        for ((_, backend) in ALL_BACKENDS) {
            eval("contains(\"hello\", \"z\")", backend) shouldBe false
        }
    }

    @Test
    fun `string equality`() {
        for ((_, backend) in ALL_BACKENDS) {
            compilerWithLogic.compile("\"a\" == \"a\"", backend = backend)
                .eval(MapEvaluationContext(emptyMap())) shouldBe true
            compilerWithLogic.compile("\"a\" != \"b\"", backend = backend)
                .eval(MapEvaluationContext(emptyMap())) shouldBe true
        }
    }

    @Test
    fun `string variable concatenation`() {
        for ((_, backend) in ALL_BACKENDS) {
            compilerWithVariables.compile("\$s + \"!\"", mapOf("s" to String::class), backend)
                .eval(MapEvaluationContext(mapOf("s" to "hi"))) shouldBe "hi!"
        }
    }

    @Test
    fun `concat of string and number is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("\"a\" + 1", backend) }
        }
    }

    @Test
    fun `length of number is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("length(1)", backend) }
        }
    }

    @Test
    fun `contains with number argument is a compile error`() {
        for ((_, backend) in ALL_BACKENDS) {
            shouldThrow<ExpressionException> { eval("contains(\"a\", 1)", backend) }
        }
    }
}
