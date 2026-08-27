package ru.cramen.suffeeex.ext.calculus

import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.node.NumericOp
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.node.differentiateOrThrow
import ru.cramen.suffeeex.ext.math.function.MathFunctionNode
import ru.cramen.suffeeex.ext.math.number.NumberLiteralNode
import ru.cramen.suffeeex.ext.math.operator.BinaryArithmeticNode
import ru.cramen.suffeeex.ext.math.operator.NegationNode

/**
 * Symbolic differentiation entry point. The differentiation rules themselves
 * live in the node classes (the core `DifferentiableNode` interface); this
 * object only triggers the rule and simplifies the resulting tree.
 */
object Differentiator {

    /** Differentiates [root] by the variable [by] and simplifies the derivative tree. */
    fun differentiate(root: TypedNode, by: String): TypedNode = simplify(root.differentiateOrThrow(by))

    /**
     * Recursive simplification pass over the known public node classes:
     * children are simplified first, constant subtrees are folded by
     * evaluation, and trivial arithmetic identities (`0 + x`, `x * 1`, ...)
     * are collapsed. Nodes of any other class (e.g. from extensions this
     * object does not know) are returned unchanged.
     */
    fun simplify(node: TypedNode): TypedNode = when (node) {
        is BinaryArithmeticNode -> simplifyArithmetic(node)
        is NegationNode -> simplifyNegation(node)
        is MathFunctionNode -> simplifyFunction(node)
        else -> node
    }

    private fun simplifyArithmetic(node: BinaryArithmeticNode): TypedNode {
        val left = simplify(node.left)
        val right = simplify(node.right)
        if (isConstant(left) && isConstant(right)) {
            return fold(BinaryArithmeticNode(node.op, left, right))
        }
        return when (node.op) {
            NumericOp.ADD -> when {
                left.isZero() -> right
                right.isZero() -> left
                else -> BinaryArithmeticNode(node.op, left, right)
            }
            NumericOp.SUB -> when {
                right.isZero() -> left
                else -> BinaryArithmeticNode(node.op, left, right)
            }
            NumericOp.MUL -> when {
                left.isZero() -> left
                right.isZero() -> right
                left.isOne() -> right
                right.isOne() -> left
                else -> BinaryArithmeticNode(node.op, left, right)
            }
            NumericOp.DIV -> when {
                left.isZero() -> left
                right.isOne() -> left
                else -> BinaryArithmeticNode(node.op, left, right)
            }
            NumericOp.REM -> BinaryArithmeticNode(node.op, left, right)
        }
    }

    private fun simplifyNegation(node: NegationNode): TypedNode {
        val operand = simplify(node.operand)
        if (isConstant(operand)) return fold(NegationNode(operand))
        if (operand is NegationNode) return operand.operand
        return NegationNode(operand)
    }

    private fun simplifyFunction(node: MathFunctionNode): TypedNode {
        // MathFunctionNode does not expose its implementation, so a call with
        // non-constant arguments cannot be rebuilt with simplified args; only
        // fully constant calls are folded.
        return if (node.args.all(::isConstant)) fold(node) else node
    }

    /** A subtree that evaluates without any variables. */
    private fun isConstant(node: TypedNode): Boolean = when (node) {
        is NumberLiteralNode -> true
        is NegationNode -> isConstant(node.operand)
        is BinaryArithmeticNode -> isConstant(node.left) && isConstant(node.right)
        is MathFunctionNode -> node.args.all(::isConstant)
        else -> false
    }

    /** Evaluates a constant subtree back into a literal; a folding failure leaves the tree as is. */
    private fun fold(node: TypedNode): TypedNode {
        val value = try {
            node.build().eval(MapEvaluationContext(emptyMap()))
        } catch (e: RuntimeException) {
            return node
        }
        return if (value is Number) NumberLiteralNode(value, node.type) else node
    }

    private fun TypedNode.isZero(): Boolean = this is NumberLiteralNode && value.toDouble() == 0.0

    private fun TypedNode.isOne(): Boolean = this is NumberLiteralNode && value.toDouble() == 1.0
}
