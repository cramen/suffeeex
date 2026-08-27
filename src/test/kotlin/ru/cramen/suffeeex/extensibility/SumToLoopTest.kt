package ru.cramen.suffeeex.extensibility

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.core.backend.asm.AsmBackend
import ru.cramen.suffeeex.core.node.CompareOp
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.NumericOp
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.FunctionParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.LOW_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.ext.math.bracket.BracketExtension
import ru.cramen.suffeeex.ext.math.function.IdentifierTokenType
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import ru.cramen.suffeeex.ext.variable.VariableExtension
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * Proof that loops are addable without core changes: `sumTo(n)` sums 1..n.
 * The ASM backend runs a generated jump loop built only from the generic
 * emission primitives (labels, jumps, locals); the composition backend just
 * runs the equivalent Kotlin loop.
 */
internal class SumToLoopTest {

    /** sum of 1..n; [arg] must be Int. */
    class SumToNode(val arg: TypedNode) : TypedNode {
        override val type: KClass<*> = Int::class

        init {
            if (arg.type != Int::class) {
                throw ExpressionException("function 'sumTo' expects an Int argument, got ${arg.type.simpleName}")
            }
        }

        override fun build(): Expression {
            val e = arg.build()
            return Expression { c ->
                val n = e.eval(c) as Int
                var sum = 0
                for (i in 1..n) sum += i
                sum
            }
        }

        override fun emit(emission: Emission) {
            val n = emission.newLocal(Int::class)
            val i = emission.newLocal(Int::class)
            val sum = emission.newLocal(Int::class)
            emission.push(arg)
            emission.storeLocal(n, Int::class)
            emission.ldc(1)
            emission.storeLocal(i, Int::class)
            emission.ldc(0)
            emission.storeLocal(sum, Int::class)
            val loop = emission.newLabel()
            val end = emission.newLabel()
            emission.mark(loop)
            emission.loadLocal(i, Int::class)
            emission.loadLocal(n, Int::class)
            emission.compare(CompareOp.LE, Int::class)
            emission.jumpIfFalse(end)
            emission.loadLocal(sum, Int::class)
            emission.loadLocal(i, Int::class)
            emission.numericBinary(NumericOp.ADD, Int::class)
            emission.storeLocal(sum, Int::class)
            emission.loadLocal(i, Int::class)
            emission.ldc(1)
            emission.numericBinary(NumericOp.ADD, Int::class)
            emission.storeLocal(i, Int::class)
            emission.jump(loop)
            emission.mark(end)
            emission.loadLocal(sum, Int::class)
        }
    }

    object SumToExtension : SyntaxExtension {
        override fun register(registry: ExtensionRegistry) {
            registry.registerTokenParser(
                RegexpTokenParser(IdentifierTokenType, Regex("[a-zA-Z_][a-zA-Z0-9_]*"), LOW_TOKEN_PRIORITY)
            )
            registry.registerFunctionNameTokenType(IdentifierTokenType)
            registry.registerFunction(object : FunctionParser {
                override val name = "sumTo"
                override val minArgs = 1
                override val maxArgs = 1

                override fun compile(args: List<TypedNode>): TypedNode = SumToNode(args.single())
            })
        }
    }

    fun interface SumTo {
        fun eval(n: Int): Int
    }

    private val compiler = ExpressionCompiler(NumberExtension, BracketExtension, SumToExtension)
    private val variableCompiler =
        ExpressionCompiler(NumberExtension, BracketExtension, VariableExtension, SumToExtension)

    private fun eval(source: String, backend: ExpressionBackend): Any? =
        compiler.compile(source, backend = backend).eval(MapEvaluationContext(emptyMap()))

    @Test
    fun `sumTo of a literal on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                eval("sumTo(5)", backend) shouldBe 15
                eval("sumTo(1)", backend) shouldBe 1
                eval("sumTo(100)", backend) shouldBe 5050
            }
        }
    }

    @Test
    fun `sumTo of a variable on every backend`() {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                variableCompiler.compile("sumTo(\$n)", mapOf("n" to Int::class), backend)
                    .eval(MapEvaluationContext(mapOf("n" to 10))) shouldBe 55
            }
        }
    }

    @Test
    fun `sumTo specialized on asm allocates locals after parameters`() {
        val tree = variableCompiler.parseTree("sumTo(\$n)", mapOf("n" to Int::class))
        val sumTo = AsmBackend.compile(tree, SumTo::class) as SumTo
        sumTo.eval(10) shouldBe 55
        sumTo.eval(5) shouldBe 15
    }
}
