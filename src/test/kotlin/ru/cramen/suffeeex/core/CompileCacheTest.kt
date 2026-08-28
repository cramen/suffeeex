package ru.cramen.suffeeex.core

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.ext.math.MathSyntax
import kotlin.reflect.KClass
import kotlin.test.Test

internal class CompileCacheTest {

    fun interface IntOp {
        fun apply(a: Int): Int
    }

    fun interface OtherIntOp {
        fun apply(a: Int): Int
    }

    private val compiler = ExpressionCompiler(MathSyntax)

    @Test
    fun `same source and varTypes return the same instance on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val first = compiler.compile("\$x * 2", mapOf("x" to Int::class), backend)
                val second = compiler.compile("\$x * 2", mapOf("x" to Int::class), backend)
                second shouldBeSameInstanceAs first
                second.eval(MapEvaluationContext(mapOf("x" to 21))) shouldBe 42
            }
        }
    }

    @Test
    fun `soft-referenced cache returns the same instance while the caller holds it`() {
        // the cache holds values through soft references; while a strong
        // reference is held (and memory is fine), the same key must resolve
        // to the same instance — GC eviction is not forced here
        val held = compiler.compile("40 + 2")
        repeat(5) { compiler.compile("40 + 2") shouldBeSameInstanceAs held }
        held.eval(MapEvaluationContext(emptyMap())) shouldBe 42
    }

    @Test
    fun `different varTypes produce different instances`() {
        val intVersion = compiler.compile("\$x + \$x", mapOf("x" to Int::class))
        val longVersion = compiler.compile("\$x + \$x", mapOf("x" to Long::class))
        longVersion shouldNotBeSameInstanceAs intVersion
        intVersion.eval(MapEvaluationContext(mapOf("x" to 21))) shouldBe 42
        longVersion.eval(MapEvaluationContext(mapOf("x" to 21L))) shouldBe 42L
    }

    @Test
    fun `different sources produce different instances`() {
        val first = compiler.compile("1 + 1")
        val second = compiler.compile("1 + 2")
        second shouldNotBeSameInstanceAs first
    }

    @Test
    fun `different backends produce different instances`() {
        val byBackend = ALL_BACKENDS.map { (_, backend) -> compiler.compile("1 + 1", emptyMap(), backend) }
        byBackend[1] shouldNotBeSameInstanceAs byBackend[0]
    }

    @Test
    fun `specialized compile caches by target on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val first = compiler.compile("\$a + 1", IntOp::class, backend)
                val second = compiler.compile("\$a + 1", IntOp::class, backend)
                second shouldBeSameInstanceAs first
                second.apply(41) shouldBe 42
            }
        }
    }

    @Test
    fun `specialized compile with a different target produces a different instance`() {
        val first: IntOp = compiler.compile("\$a + 1", IntOp::class)
        val second: OtherIntOp = compiler.compile("\$a + 1", OtherIntOp::class)
        second shouldNotBeSameInstanceAs first
        second.apply(41) shouldBe 42
    }

    @Test
    fun `parseTree and compileTree are not cached`() {
        val root = compiler.parseTree("1 + 1")
        compiler.compileTree(root) shouldNotBeSameInstanceAs compiler.compileTree(root)
    }

    @Test
    fun `cache is bounded - the eldest entry is evicted past the limit even when strongly held`() {
        val held = compiler.compile("1 + 0")
        // the cache keeps at most 1024 entries; adding more distinct sources
        // evicts the eldest key regardless of the value's reachability
        repeat(1100) { compiler.compile("1 + ${it + 1}") }
        compiler.compile("1 + 0") shouldNotBeSameInstanceAs held
    }

    private fun compile(source: String, varTypes: Map<String, KClass<*>> = emptyMap()) =
        compiler.compile(source, varTypes)
}
