package ru.cramen.suffeeex.core.node

import kotlin.reflect.KClass

enum class CompareOp { LT, LE, GT, GE, EQ, NE }

/**
 * Bytecode emission abstraction owned by core, so that core never depends
 * on a concrete bytecode library (e.g. ASM).
 *
 * Invariant: emitting a node leaves exactly one value of [TypedNode.type]
 * on the stack. Boolean is represented as int 0/1 on the stack.
 *
 * Equality split: [objectsEquals] is the only reference-equality primitive;
 * a `!=` node inverts it itself (e.g. via [logicalNot]) — the emission
 * layer provides no inverted form.
 */
interface Emission {
    fun push(node: TypedNode) {
        node.emit(this)
    }

    fun constant(type: KClass<*>, value: Any)

    fun loadVariable(name: String, type: KClass<*>)

    fun numericBinary(op: NumericOp, type: KClass<*>)

    fun numericNegate(type: KClass<*>)

    fun convertNumeric(from: KClass<*>, to: KClass<*>)

    fun invokeMath(name: String, argTypes: List<KClass<*>>, resultType: KClass<*>)

    /** Pops two operands of [type], pushes Boolean (0/1). */
    fun compare(op: CompareOp, type: KClass<*>)

    /**
     * If-expression: emits [condition] (Boolean), then exactly one of the
     * branches; their types are already checked equal by the node.
     */
    fun branch(condition: TypedNode, ifTrue: TypedNode, ifFalse: TypedNode)

    /** Short-circuit `&&` over two Boolean sub-nodes; pushes Boolean. */
    fun logicalAnd(left: TypedNode, right: TypedNode)

    /** Short-circuit `||` over two Boolean sub-nodes; pushes Boolean. */
    fun logicalOr(left: TypedNode, right: TypedNode)

    /** Pops Boolean, pushes the inverted Boolean. */
    fun logicalNot()

    /** Pops two Strings, pushes their concatenation. */
    fun stringConcat()

    /** Pops two references, pushes Boolean (java.util.Objects.equals). */
    fun objectsEquals()

    /**
     * Pops the receiver String and [argTypes] arguments, invokes
     * `String.<name>` virtually, pushes [resultType]
     * (e.g. `length()` -> Int, `startsWith(String)` -> Boolean).
     * [argTypes] must be the declared JVM parameter types of the method
     * (mind overloads: `contains` takes CharSequence, not String).
     */
    fun invokeStringMethod(name: String, argTypes: List<KClass<*>>, resultType: KClass<*>)
}
