package ru.cramen.suffeeex.core.node

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import ru.cramen.suffeeex.ALL_BACKENDS
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.MapEvaluationContext
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * Smoke tests for the non-numeric Emission primitives, driven by tiny
 * hand-written nodes. Every case runs on both backends, so the composition
 * behavior of the same nodes is covered too.
 */
internal class EmissionTest {

    private class ConstNode(override val type: KClass<*>, private val value: Any) : TypedNode {
        override fun build(): Expression = Expression { value }
        override fun emit(emission: Emission) = emission.constant(type, value)
    }

    /** Like the variable extension's node, but hand-rolled to stay core-only. */
    private class VarNode(private val name: String, override val type: KClass<*>) : TypedNode {
        override fun build(): Expression = Expression { context ->
            context.resolve(name)
                ?: throw ExpressionException("variable '$name' is not present in the evaluation context")
        }

        override fun emit(emission: Emission) = emission.loadVariable(name, type)
    }

    private class CompareNode(private val op: CompareOp, private val left: TypedNode, private val right: TypedNode) :
        TypedNode {
        override val type = Boolean::class

        override fun build(): Expression {
            val l = left.build()
            val r = right.build()
            return Expression { context -> compareValues(op, l.eval(context), r.eval(context)) }
        }

        override fun emit(emission: Emission) {
            emission.push(left)
            emission.push(right)
            emission.compare(op, left.type)
        }

        private fun compareValues(op: CompareOp, a: Any?, b: Any?): Boolean {
            // native operators, not compareTo: Double.compareTo considers
            // NaN equal to itself, while the JVM FCMPG/DCMPG route does not
            if (a is Double && b is Double) {
                return when (op) {
                    CompareOp.LT -> a < b
                    CompareOp.LE -> a <= b
                    CompareOp.GT -> a > b
                    CompareOp.GE -> a >= b
                    CompareOp.EQ -> a == b
                    CompareOp.NE -> a != b
                }
            }
            @Suppress("UNCHECKED_CAST")
            val comparison = (a as Comparable<Any?>).compareTo(b)
            return when (op) {
                CompareOp.LT -> comparison < 0
                CompareOp.LE -> comparison <= 0
                CompareOp.GT -> comparison > 0
                CompareOp.GE -> comparison >= 0
                CompareOp.EQ -> comparison == 0
                CompareOp.NE -> comparison != 0
            }
        }
    }

    private class BranchNode(
        private val condition: TypedNode,
        private val ifTrue: TypedNode,
        private val ifFalse: TypedNode,
    ) : TypedNode {
        override val type = ifTrue.type

        override fun build(): Expression {
            val c = condition.build()
            val t = ifTrue.build()
            val f = ifFalse.build()
            return Expression { context -> if (c.eval(context) as Boolean) t.eval(context) else f.eval(context) }
        }

        override fun emit(emission: Emission) = emission.branch(condition, ifTrue, ifFalse)
    }

    private class AndNode(private val left: TypedNode, private val right: TypedNode) : TypedNode {
        override val type = Boolean::class
        override fun build(): Expression {
            val l = left.build()
            val r = right.build()
            return Expression { context -> l.eval(context) as Boolean && r.eval(context) as Boolean }
        }

        override fun emit(emission: Emission) = emission.logicalAnd(left, right)
    }

    private class OrNode(private val left: TypedNode, private val right: TypedNode) : TypedNode {
        override val type = Boolean::class
        override fun build(): Expression {
            val l = left.build()
            val r = right.build()
            return Expression { context -> l.eval(context) as Boolean || r.eval(context) as Boolean }
        }

        override fun emit(emission: Emission) = emission.logicalOr(left, right)
    }

    private class NotNode(private val operand: TypedNode) : TypedNode {
        override val type = Boolean::class
        override fun build(): Expression {
            val e = operand.build()
            return Expression { context -> !(e.eval(context) as Boolean) }
        }

        override fun emit(emission: Emission) {
            emission.push(operand)
            emission.logicalNot()
        }
    }

    private class ConcatNode(private val left: TypedNode, private val right: TypedNode) : TypedNode {
        override val type = String::class
        override fun build(): Expression {
            val l = left.build()
            val r = right.build()
            return Expression { context -> l.eval(context) as String + (r.eval(context) as String) }
        }

        override fun emit(emission: Emission) {
            emission.push(left)
            emission.push(right)
            emission.stringConcat()
        }
    }

    private class EqualsNode(private val left: TypedNode, private val right: TypedNode) : TypedNode {
        override val type = Boolean::class
        override fun build(): Expression {
            val l = left.build()
            val r = right.build()
            return Expression { context -> l.eval(context) == r.eval(context) }
        }

        override fun emit(emission: Emission) {
            emission.push(left)
            emission.push(right)
            emission.objectsEquals()
        }
    }

    private class StringMethodNode(
        private val name: String,
        private val argTypes: List<KClass<*>>,
        private val resultType: KClass<*>,
        private val receiver: TypedNode,
        private val args: List<TypedNode>,
    ) : TypedNode {
        override val type = resultType

        override fun build(): Expression {
            val r = receiver.build()
            val argExprs = args.map { it.build() }
            return Expression { context ->
                val receiverValue = r.eval(context) as String
                val argValues = argExprs.map { it.eval(context) }
                when (name) {
                    "length" -> receiverValue.length
                    "startsWith" -> receiverValue.startsWith(argValues[0] as String)
                    else -> error("unsupported fake method: $name")
                }
            }
        }

        override fun emit(emission: Emission) {
            emission.push(receiver)
            args.forEach { emission.push(it) }
            emission.invokeStringMethod(name, argTypes, resultType)
        }
    }

    private fun booleanConst(value: Boolean) = ConstNode(Boolean::class, value)
    private fun intConst(value: Int) = ConstNode(Int::class, value)
    private fun longConst(value: Long) = ConstNode(Long::class, value)
    private fun doubleConst(value: Double) = ConstNode(Double::class, value)
    private fun stringConst(value: String) = ConstNode(String::class, value)

    private fun assertEval(root: TypedNode, expected: Any?, context: Map<String, Any?> = emptyMap()) {
        for ((name, backend) in ALL_BACKENDS) {
            withClue("backend: $name") {
                backend.compile(root).eval(MapEvaluationContext(context)) shouldBe expected
            }
        }
    }

    @Test
    fun `compare ints`() {
        assertEval(CompareNode(CompareOp.LT, intConst(1), intConst(2)), true)
        assertEval(CompareNode(CompareOp.LT, intConst(3), intConst(2)), false)
        assertEval(CompareNode(CompareOp.GE, intConst(2), intConst(2)), true)
    }

    @Test
    fun `compare longs`() {
        assertEval(CompareNode(CompareOp.EQ, longConst(5), longConst(5)), true)
        assertEval(CompareNode(CompareOp.NE, longConst(5), longConst(6)), true)
    }

    @Test
    fun `compare doubles follows javac nan semantics`() {
        assertEval(CompareNode(CompareOp.LT, doubleConst(1.0), doubleConst(2.5)), true)
        // every ordered comparison with NaN is false, != is true
        assertEval(CompareNode(CompareOp.LT, doubleConst(Double.NaN), doubleConst(1.0)), false)
        assertEval(CompareNode(CompareOp.GT, doubleConst(1.0), doubleConst(Double.NaN)), false)
        assertEval(CompareNode(CompareOp.NE, doubleConst(Double.NaN), doubleConst(Double.NaN)), true)
    }

    @Test
    fun `compare booleans`() {
        assertEval(CompareNode(CompareOp.EQ, booleanConst(true), booleanConst(true)), true)
        assertEval(CompareNode(CompareOp.NE, booleanConst(true), booleanConst(false)), true)
    }

    @Test
    fun `branch picks a side`() {
        val onTrue = BranchNode(booleanConst(true), stringConst("yes"), stringConst("no"))
        val onFalse = BranchNode(booleanConst(false), stringConst("yes"), stringConst("no"))
        assertEval(onTrue, "yes")
        assertEval(onFalse, "no")
    }

    @Test
    fun `branch does not evaluate the dead side`() {
        // $missing would fail to resolve if evaluated
        val node = BranchNode(booleanConst(true), stringConst("ok"), VarNode("missing", String::class))
        assertEval(node, "ok")
    }

    @Test
    fun `logical and`() {
        assertEval(AndNode(booleanConst(true), booleanConst(false)), false)
        assertEval(AndNode(booleanConst(true), booleanConst(true)), true)
    }

    @Test
    fun `logical and short-circuits`() {
        assertEval(AndNode(booleanConst(false), VarNode("missing", Boolean::class)), false)
    }

    @Test
    fun `logical or`() {
        assertEval(OrNode(booleanConst(false), booleanConst(true)), true)
        assertEval(OrNode(booleanConst(false), booleanConst(false)), false)
    }

    @Test
    fun `logical or short-circuits`() {
        assertEval(OrNode(booleanConst(true), VarNode("missing", Boolean::class)), true)
    }

    @Test
    fun `logical not`() {
        assertEval(NotNode(booleanConst(true)), false)
        assertEval(NotNode(booleanConst(false)), true)
    }

    @Test
    fun `string concat`() {
        assertEval(ConcatNode(stringConst("foo"), stringConst("bar")), "foobar")
    }

    @Test
    fun `objects equals`() {
        assertEval(EqualsNode(stringConst("a"), stringConst("a")), true)
        assertEval(EqualsNode(stringConst("a"), stringConst("b")), false)
    }

    @Test
    fun `string inequality is objects-equals inverted by the node`() {
        assertEval(NotNode(EqualsNode(stringConst("a"), stringConst("b"))), true)
    }

    @Test
    fun `invoke string method`() {
        assertEval(StringMethodNode("length", emptyList(), Int::class, stringConst("hello"), emptyList()), 5)
        assertEval(
            StringMethodNode(
                "startsWith",
                listOf(String::class),
                Boolean::class,
                stringConst("hello"),
                listOf(stringConst("he")),
            ),
            true,
        )
    }

    @Test
    fun `boolean variable loads from context`() {
        assertEval(NotNode(VarNode("flag", Boolean::class)), false, mapOf("flag" to true))
    }

    @Test
    fun `string variable loads from context`() {
        assertEval(ConcatNode(VarNode("s", String::class), stringConst("!")), "hi!", mapOf("s" to "hi"))
    }
}
