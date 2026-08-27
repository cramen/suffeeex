package ru.cramen.suffeeex.core.node

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.MapEvaluationContext
import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * Hand-written nodes over the generic emission hatches: new types and
 * control flow without touching core.
 */
internal class GenericEmissionTest {

    // an unregistered reference type: fallback TypeEmission + newObject/ldc/invokeConstructor
    private class BigDecimalConstantNode(private val text: String) : TypedNode {
        override val type: KClass<*> = BigDecimal::class
        override fun build(): Expression = Expression { BigDecimal(text) }
        override fun emit(emission: Emission) {
            emission.newObject(BigDecimal::class)
            emission.ldc(text)
            emission.invokeConstructor(BigDecimal::class, listOf(String::class))
        }
    }

    // labels, jumps and locals: |value| for Int
    private class AbsNode(private val value: Int) : TypedNode {
        override val type: KClass<*> = Int::class
        override fun build(): Expression = Expression { kotlin.math.abs(value) }
        override fun emit(emission: Emission) {
            val slot = emission.newLocal(Int::class)
            emission.ldc(value)
            emission.storeLocal(slot, Int::class)
            emission.loadLocal(slot, Int::class)
            emission.ldc(0)
            emission.compare(CompareOp.LT, Int::class)
            val end = emission.newLabel()
            emission.jumpIfFalse(end)
            emission.loadLocal(slot, Int::class)
            emission.numericNegate(Int::class)
            emission.storeLocal(slot, Int::class)
            emission.mark(end)
            emission.loadLocal(slot, Int::class)
        }
    }

    @Test
    fun `unregistered reference type works on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val expression = backend.compile(BigDecimalConstantNode("2.5"))
                expression.eval(MapEvaluationContext(emptyMap())) shouldBe BigDecimal("2.5")
            }
        }
    }

    @Test
    fun `labels jumps and locals work on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                backend.compile(AbsNode(-7)).eval(MapEvaluationContext(emptyMap())) shouldBe 7
                backend.compile(AbsNode(3)).eval(MapEvaluationContext(emptyMap())) shouldBe 3
            }
        }
    }
}
