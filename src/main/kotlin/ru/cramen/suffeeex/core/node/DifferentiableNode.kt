package ru.cramen.suffeeex.core.node

import ru.cramen.suffeeex.core.ExpressionException

/** A node that knows how to symbolically differentiate itself by a variable. */
interface DifferentiableNode {
    fun differentiate(by: String): TypedNode
}

/** Uniform entry used by differentiation rules: clear error for non-differentiable nodes. */
fun TypedNode.differentiateOrThrow(by: String): TypedNode =
    (this as? DifferentiableNode)?.differentiate(by)
        ?: throw ExpressionException("node ${javaClass.simpleName} does not support differentiation")
