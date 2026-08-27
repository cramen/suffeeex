package ru.cramen.suffeeex.ext.calculus

import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.asm.AsmBackend
import ru.cramen.suffeeex.ext.math.MathSyntax
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * Verifies the complex differentiation rules (chains, products, quotients
 * of nontrivial subexpressions) against a numerical derivative computed by
 * central differences on the original expression.
 */
internal class ComplexDifferentiationTest {

    fun interface D1 {
        fun eval(x: Double): Double
    }

    private val compiler = ExpressionCompiler(MathSyntax)
    private val xDouble = mapOf<String, KClass<*>>("x" to Double::class)

    private fun numericDerivative(source: String, x: Double, h: Double = 1e-5): Double {
        val f = compiler.compile(source, xDouble)
        val fp = f.eval(MapEvaluationContext(mapOf("x" to x + h))) as Double
        val fm = f.eval(MapEvaluationContext(mapOf("x" to x - h))) as Double
        return (fp - fm) / (2 * h)
    }

    private fun checkDerivative(source: String, points: List<Double>) {
        val tree = Differentiator.differentiate(compiler.parseTree(source, xDouble), "x")
        for ((name, backend) in ALL_BACKENDS) {
            val d = compiler.compileTree(tree, backend)
            for (x in points) {
                withClue("backend=$name, x=$x, source='$source'") {
                    (d.eval(MapEvaluationContext(mapOf("x" to x))) as Double) shouldBe
                        (numericDerivative(source, x) plusOrMinus 1e-4)
                }
            }
        }
    }

    private val points = listOf(0.3, 1.1, 2.2)

    @Test
    fun `double chain - sin of cos`() =
        checkDerivative("sin(cos(\$x))", points)

    @Test
    fun `chain over a product - exp of x times sin`() =
        checkDerivative("exp(\$x * sin(\$x))", points)

    @Test
    fun `chain over a sum of squares - sqrt`() =
        checkDerivative("sqrt(\$x * \$x + 1.0)", points)

    @Test
    fun `chain over ln of a polynomial`() =
        checkDerivative("ln(\$x * \$x + 1.0)", points)

    @Test
    fun `chain over log10`() =
        checkDerivative("log10(\$x + 1.0)", points)

    @Test
    fun `pow of a function - sin squared`() =
        checkDerivative("pow(sin(\$x), 2.0)", points)

    @Test
    fun `function of a pow - sin of x squared`() =
        checkDerivative("sin(pow(\$x, 2.0))", points)

    @Test
    fun `gaussian - exp of x squared`() =
        checkDerivative("exp(\$x * \$x)", points)

    @Test
    fun `triple product`() =
        checkDerivative("\$x * sin(\$x) * cos(\$x)", points)

    @Test
    fun `quotient with nontrivial numerator and denominator`() =
        checkDerivative("(\$x + 1.0) / (\$x * \$x + 1.0)", points)

    @Test
    fun `quotient with a function in the numerator`() =
        checkDerivative("sin(\$x) / (\$x + 1.0)", points)

    @Test
    fun `quotient of pow over a polynomial`() =
        checkDerivative("pow(\$x, 3.0) / (pow(\$x, 2.0) + 1.0)", points)

    @Test
    fun `sum of chains - tan plus ln`() =
        checkDerivative("tan(\$x) + ln(\$x)", points)

    @Test
    fun `negation inside a product`() =
        checkDerivative("-sin(\$x) * \$x", points)

    @Test
    fun `triple chain - exp of sin of cos`() =
        checkDerivative("exp(sin(cos(\$x)))", points)

    @Test
    fun `deep mixed composition`() =
        checkDerivative("sqrt(pow(\$x, 2.0) + 1.0) * ln(\$x + 1.0) / (sin(\$x) + 2.0)", points)

    @Test
    fun `specialized compile of a complex derivative`() {
        val tree = Differentiator.differentiate(
            compiler.parseTree("sqrt(\$x * \$x + 1.0)", xDouble), "x",
        )
        val d = AsmBackend.compile(tree, D1::class) as D1
        for (x in points) {
            withClue("x=$x") {
                d.eval(x) shouldBe (numericDerivative("sqrt(\$x * \$x + 1.0)", x) plusOrMinus 1e-4)
            }
        }
    }
}
