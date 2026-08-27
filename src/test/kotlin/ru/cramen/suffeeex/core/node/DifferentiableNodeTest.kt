package ru.cramen.suffeeex.core.node

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import kotlin.reflect.KClass
import kotlin.test.Test

internal class DifferentiableNodeTest {

    private open class ConstNode(val value: Long) : TypedNode {
        override val type: KClass<*> = Long::class
        override fun build(): Expression = Expression { value }
    }

    private class DifferentiableConstNode(value: Long) : ConstNode(value), DifferentiableNode {
        override fun differentiate(by: String): TypedNode = ConstNode(0L)
    }

    @Test
    fun `non-differentiable node fails with a clear error`() {
        val exception = shouldThrow<ExpressionException> { ConstNode(1L).differentiateOrThrow("x") }
        exception.message shouldContain "ConstNode"
        exception.message shouldContain "does not support differentiation"
    }

    @Test
    fun `differentiable node differentiates through the helper`() {
        val derivative = DifferentiableConstNode(42L).differentiateOrThrow("x")
        derivative.type shouldBe Long::class
        derivative.build().eval(MapEvaluationContext(emptyMap())) shouldBe 0L
    }
}
