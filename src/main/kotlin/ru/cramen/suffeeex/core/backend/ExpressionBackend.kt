package ru.cramen.suffeeex.core.backend

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.node.TypedNode

interface ExpressionBackend {
    fun compile(root: TypedNode): Expression
}
