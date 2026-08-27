package ru.cramen.suffeeex.core.backend.asm

import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.ext.math.MathSyntax
import kotlin.test.Test

internal class AsmClassLoaderTest {

    private val compiler = ExpressionCompiler(MathSyntax)

    @Test
    fun `each generated class is defined in its own classloader`() {
        val first = compiler.compileTree(compiler.parseTree("1 + 1"), AsmBackend)
        val second = compiler.compileTree(compiler.parseTree("2 + 2"), AsmBackend)
        second.javaClass.classLoader shouldNotBeSameInstanceAs first.javaClass.classLoader
    }

    @Test
    fun `specialized classes get their own classloaders too`() {
        val root = compiler.parseTree("\$a + 1", mapOf("a" to Int::class))
        val first = AsmBackend.compile(root, IntOp::class)
        val second = AsmBackend.compile(root, IntOp::class)
        second.javaClass.classLoader shouldNotBeSameInstanceAs first.javaClass.classLoader
    }

    fun interface IntOp {
        fun apply(a: Int): Int
    }
}
