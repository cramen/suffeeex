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

    /**
     * Pops two operands of [type], pushes Boolean (0/1).
     *
     * Primitive/numeric stack categories only (INT, LONG, FLOAT, DOUBLE);
     * [StackCategory.REFERENCE] is rejected with an [ExpressionException].
     * Custom reference types express comparisons via [invokeVirtual] /
     * [invokeStatic] — see the BigDecimal extensibility test
     * (`src/test/kotlin/ru/cramen/suffeeex/extensibility/BigDecimalTypeTest.kt`)
     * for a worked example.
     */
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

    // --- generic escape hatches: new types and control flow without touching core ---

    /** Pushes any LDC-supported constant (Int, Long, Float, Double, String). */
    fun ldc(value: Any)

    /** NEW + DUP a fresh instance of [type]; follow with [invokeConstructor]. */
    fun newObject(type: KClass<*>)

    /** Invokes the constructor of [type] with the given argument types (consumes the DUP'd reference). */
    fun invokeConstructor(type: KClass<*>, argTypes: List<KClass<*>> = emptyList())

    /** Pops [argTypes] arguments, invokes the static method, pushes [resultType]. */
    fun invokeStatic(owner: KClass<*>, name: String, argTypes: List<KClass<*>>, resultType: KClass<*>)

    /** Pops the receiver and [argTypes] arguments, invokes the virtual method, pushes [resultType]. */
    fun invokeVirtual(owner: KClass<*>, name: String, argTypes: List<KClass<*>>, resultType: KClass<*>)

    /** Discards the top of the stack (POP or POP2 depending on the type's category). */
    fun pop(type: KClass<*>)

    /** A new branch target for control flow built by extensions (conditions, loops). */
    fun newLabel(): EmissionLabel

    /** Marks the current position as [label]. */
    fun mark(label: EmissionLabel)

    /** Unconditional jump to [label]. */
    fun jump(label: EmissionLabel)

    /** Pops a Boolean (int 0/1), jumps to [label] when it is false. */
    fun jumpIfFalse(label: EmissionLabel)

    /** Pops a Boolean (int 0/1), jumps to [label] when it is true. */
    fun jumpIfTrue(label: EmissionLabel)

    /**
     * Allocates a local variable slot for [type] (LONG/DOUBLE take two
     * slots) and returns its index. Slots are allocated monotonically for
     * the whole method; reuse the returned index with [loadLocal]/[storeLocal].
     */
    fun newLocal(type: KClass<*>): Int

    /** Pushes the local variable of [type] at [slot]. */
    fun loadLocal(slot: Int, type: KClass<*>)

    /** Pops a value of [type] into the local variable at [slot]. */
    fun storeLocal(slot: Int, type: KClass<*>)
}

/** Opaque branch target created by [Emission.newLabel]. */
interface EmissionLabel
