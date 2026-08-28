package ru.cramen.suffeeex.suggest

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.ext.StandardSyntax
import ru.cramen.suffeeex.ext.property.PropertyAccessExtension
import ru.cramen.suffeeex.extensibility.SumToLoopTest
import kotlin.test.Test

class SuggesterTest {
    private val compiler = ExpressionCompiler(StandardSyntax)
    private val suggester = Suggester(compiler.registry)

    private fun texts(suggestions: List<Suggestion>) = suggestions.map { it.text }

    @Test
    fun `empty source suggests operand candidates`() {
        val suggestions = suggester.suggest("", varTypes = mapOf("x" to Int::class))
        val texts = texts(suggestions)
        texts shouldContain "cos"
        texts shouldContain "contains"
        texts shouldContain "("
        texts shouldContain "-"
        texts shouldContain "!"
        texts shouldContain "true"
        texts shouldContain "false"
        texts shouldContain "\$x"
        suggestions.first { it.text == "\$x" }.kind shouldBe SuggestionKind.VARIABLE
        // operators that only follow an operand are not suggested at operand position
        texts shouldNotContain "*"
        texts shouldNotContain ")"
    }

    @Test
    fun `tail filters candidates by prefix`() {
        val suggestions = suggester.suggest("co")
        val texts = texts(suggestions)
        texts shouldContain "cos"
        texts shouldContain "contains"
        texts shouldNotContain "sin"
        texts shouldNotContain "pow"
        val cos = suggestions.first { it.text == "cos" }
        cos.kind shouldBe SuggestionKind.FUNCTION
        cos.detail shouldBe "cos(arg1)"
        suggestions.first { it.text == "contains" }.detail shouldBe "contains(arg1, arg2)"
    }

    @Test
    fun `after an infix operator the operand position candidates return`() {
        val suggestions = suggester.suggest("\$x + ", varTypes = mapOf("x" to Int::class))
        val texts = texts(suggestions)
        texts shouldContain "cos"
        texts shouldContain "("
        texts shouldContain "true"
    }

    @Test
    fun `after an operand only operators and brackets are suggested`() {
        val suggestions = suggester.suggest("1 + 2")
        val texts = texts(suggestions)
        texts shouldContain "+"
        texts shouldContain "*"
        texts shouldContain "&&"
        texts shouldContain ")"
        texts shouldContain ","
        suggestions.none { it.kind == SuggestionKind.FUNCTION } shouldBe true
        suggestions.none { it.kind == SuggestionKind.KEYWORD } shouldBe true
    }

    @Test
    fun `member access dot appears when a member access extension is registered`() {
        val propertySuggester = Suggester(ExpressionCompiler(StandardSyntax, PropertyAccessExtension).registry)
        val suggestion = propertySuggester.suggest("1 + 2").first { it.text == "." }
        suggestion.kind shouldBe SuggestionKind.MEMBER
    }

    @Test
    fun `inside a string literal there are no suggestions`() {
        suggester.suggest("\"ab").shouldBeEmpty()
        suggester.suggest("\"ab\" + \"cd").shouldBeEmpty()
    }

    @Test
    fun `variables come from varTypes and respect the dollar-prefixed tail`() {
        val varTypes = mapOf("x" to Int::class, "y" to String::class)
        texts(suggester.suggest("\$", varTypes = varTypes)) shouldBe listOf("\$x", "\$y")
        texts(suggester.suggest("\$x", varTypes = varTypes)) shouldBe listOf("\$x")
        // a variable mid-expression after an operator
        texts(suggester.suggest("1 + \$y", varTypes = varTypes)) shouldBe listOf("\$y")
    }

    @Test
    fun `a function from a user extension appears in suggestions`() {
        val extended = Suggester(ExpressionCompiler(StandardSyntax, SumToLoopTest.SumToExtension).registry)
        val atOperand = extended.suggest("")
        texts(atOperand) shouldContain "sumTo"
        val sumTo = atOperand.first { it.text == "sumTo" }
        sumTo.kind shouldBe SuggestionKind.FUNCTION
        sumTo.detail shouldBe "sumTo(arg1)"

        val byTail = extended.suggest("sum")
        texts(byTail) shouldContain "sumTo"
        texts(byTail) shouldNotContain "cos"
    }

    @Test
    fun `compiler suggest extension function works`() {
        val suggestions = compiler.suggest("co")
        texts(suggestions) shouldContain "cos"
        texts(suggestions) shouldContain "contains"
    }
}
