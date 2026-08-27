package ru.cramen.suffeeex.core.backend

import ru.cramen.suffeeex.core.EvaluationContext
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.node.TypeEmissions
import ru.cramen.suffeeex.core.node.TypedNode
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

/** Backend that builds a ready composition of functions from the node tree. */
object CompositionBackend : ExpressionBackend, SpecializedBackend {
    // no bytecode is generated here, so the type registry is not consulted
    override fun compile(root: TypedNode, types: TypeEmissions): Expression = root.build()

    override fun compile(root: TypedNode, target: KClass<*>, types: TypeEmissions): Any {
        val signature = specializedSignature(target)
        val expression = root.build()
        val names = signature.parameters.map { it.name }
        return Proxy.newProxyInstance(target.java.classLoader, arrayOf(target.java)) { _, _, args ->
            expression.eval(IndexedEvaluationContext(names, args ?: emptyArray()))
        }
    }

    private class IndexedEvaluationContext(
        private val names: List<String>,
        private val values: Array<out Any?>,
    ) : EvaluationContext {
        override fun resolve(name: String): Any? {
            val index = names.indexOf(name)
            return if (index >= 0) values[index] else null
        }
    }
}
