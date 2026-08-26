package ru.cramen.suffeeex

import ru.cramen.suffeeex.core.backend.CompositionBackend
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.core.backend.asm.AsmBackend

/** All expression backends, paired with a name for failure messages. */
val ALL_BACKENDS: List<Pair<String, ExpressionBackend>> = listOf(
    "composition" to CompositionBackend,
    "asm" to AsmBackend,
)
