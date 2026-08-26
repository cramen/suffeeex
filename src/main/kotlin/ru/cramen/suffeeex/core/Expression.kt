package ru.cramen.suffeeex.core

fun interface Expression {
    fun eval(context: EvaluationContext): Any?
}

interface EvaluationContext {
    fun resolve(name: String): Any?
}

class MapEvaluationContext(private val values: Map<String, Any?>) : EvaluationContext {
    override fun resolve(name: String): Any? = values[name]
}

class ExpressionException(message: String) : RuntimeException(message)
