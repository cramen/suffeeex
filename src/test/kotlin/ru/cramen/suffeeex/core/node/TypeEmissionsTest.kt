package ru.cramen.suffeeex.core.node

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import ru.cramen.suffeeex.core.ExpressionException
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlin.test.Test

internal class TypeEmissionsTest {

    // the fallback's pushConstant throws before touching the emission
    private val anyEmission = Proxy.newProxyInstance(
        Emission::class.java.classLoader,
        arrayOf(Emission::class.java),
    ) { _, _, _ -> throw UnsupportedOperationException() } as Emission

    @Test
    fun `built-in types are pre-registered`() {
        TypeEmissions.of(Int::class).descriptor shouldBe "I"
        TypeEmissions.of(Int::class).category shouldBe StackCategory.INT
        TypeEmissions.of(Int::class).wrapperInternalName shouldBe "java/lang/Integer"
        TypeEmissions.of(Long::class).category shouldBe StackCategory.LONG
        TypeEmissions.of(Double::class).descriptor shouldBe "D"
        TypeEmissions.of(Boolean::class).descriptor shouldBe "Z"
        TypeEmissions.of(Boolean::class).category shouldBe StackCategory.INT
        TypeEmissions.of(String::class).descriptor shouldBe "Ljava/lang/String;"
        TypeEmissions.of(String::class).category shouldBe StackCategory.REFERENCE
        TypeEmissions.of(String::class).wrapperInternalName shouldBe null
    }

    @Test
    fun `unregistered type gets a reference-type fallback`() {
        val fallback = TypeEmissions.of(BigDecimal::class)
        fallback.descriptor shouldBe "Ljava/math/BigDecimal;"
        fallback.category shouldBe StackCategory.REFERENCE
        fallback.wrapperInternalName shouldBe null
        fallback.unboxMethod shouldBe null
    }

    @Test
    fun `fallback cannot push constants`() {
        val exception = shouldThrow<ExpressionException> {
            TypeEmissions.of(BigDecimal::class).pushConstant(anyEmission, BigDecimal.ONE)
        }
        exception.message shouldContain "BigDecimal"
        exception.message shouldContain "TypeEmissions.register"
    }

    private class CustomType

    @Test
    fun `registered emission replaces the fallback`() {
        val custom = object : TypeEmission {
            override val type = CustomType::class
            override val descriptor = "Lru/cramen/suffeeex/core/node/TypeEmissionsTest\$CustomType;"
            override val category = StackCategory.REFERENCE
            override val wrapperInternalName: String? = null
            override val unboxMethod: String? = null
        }
        TypeEmissions.register(custom)
        TypeEmissions.of(CustomType::class) shouldBeSameInstanceAs custom
    }
}
