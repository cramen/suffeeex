package ru.cramen.suffeeex.core.node

import ru.cramen.suffeeex.core.Expression
import kotlin.reflect.KClass

/**
 * A node of a statically typed expression tree produced by the frontend.
 * The type of every node is known at parse (compile) time; backends turn
 * the tree into an executable [Expression].
 */
interface TypedNode {
    val type: KClass<*>

    /** Hook for the composition backend: builds the function composition. */
    fun build(): Expression

    /** Hook for bytecode backends. */
    fun emit(emission: Emission) {
        throw UnsupportedOperationException("${javaClass.simpleName} does not support bytecode emission")
    }
}

enum class NumericOp { ADD, SUB, MUL, DIV, REM }

internal val NUMERIC_TYPES: Set<KClass<*>> = setOf(Int::class, Long::class, Float::class, Double::class)
