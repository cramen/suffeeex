package ru.cramen.suffeeex.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.backend.asm.AsmBackend
import ru.cramen.suffeeex.core.node.NumericOp
import ru.cramen.suffeeex.ext.math.MathSyntax
import ru.cramen.suffeeex.ext.math.number.NumberLiteralNode
import ru.cramen.suffeeex.ext.math.operator.BinaryArithmeticNode
import ru.cramen.suffeeex.ext.variable.VariableNode
import kotlin.test.Test

internal class ParseTreeTest {

    fun interface DoubleOp {
        fun apply(x: Double): Double
    }

    fun interface OtherNameOp {
        fun apply(y: Double): Double
    }

    private val compiler = ExpressionCompiler(MathSyntax)

    @Test
    fun `parseTree returns the expected typed tree shape`() {
        val root = compiler.parseTree("1.5 + \$x", mapOf("x" to Double::class))

        root.shouldBeInstanceOf<BinaryArithmeticNode>()
        root.type shouldBe Double::class
        root.op shouldBe NumericOp.ADD
        root.left.shouldBeInstanceOf<NumberLiteralNode>()
        root.left.type shouldBe Double::class
        root.right.shouldBeInstanceOf<VariableNode>()
        root.right.type shouldBe Double::class
    }

    @Test
    fun `compileTree round-trip matches direct compile on every backend`() {
        val source = "1.5 + \$x * 2.0"
        val varTypes = mapOf<String, kotlin.reflect.KClass<*>>("x" to Double::class)
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                val viaTree = compiler.compileTree(compiler.parseTree(source, varTypes), backend)
                val direct = compiler.compile(source, varTypes, backend)
                for (x in listOf(0.0, 1.25, -3.5)) {
                    val context = MapEvaluationContext(mapOf("x" to x))
                    viaTree.eval(context) shouldBe direct.eval(context)
                }
            }
        }
    }

    @Test
    fun `compileTree defaults to the asm backend`() {
        val expression = compiler.compileTree(compiler.parseTree("2 + 2"))
        expression.eval(MapEvaluationContext(emptyMap())) shouldBe 4
    }

    @Test
    fun `specialized compile works for a tree from parseTree`() {
        val root = compiler.parseTree("\$x * 2.0", mapOf("x" to Double::class))
        val op = AsmBackend.compile(root, DoubleOp::class) as DoubleOp
        op.apply(21.0) shouldBe 42.0
    }

    @Test
    fun `specialized compile of a tree with a non-parameter variable fails at codegen`() {
        val root = compiler.parseTree("\$x * 2.0", mapOf("x" to Double::class))
        val exception = shouldThrow<ExpressionException> {
            AsmBackend.compile(root, OtherNameOp::class)
        }
        exception.message shouldContain "x"
        exception.message shouldContain "not a parameter"
    }
}
