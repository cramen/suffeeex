package ru.cramen.suffeeex.ext

import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.ext.logic.LogicExtension
import ru.cramen.suffeeex.ext.math.MathSyntax
import ru.cramen.suffeeex.ext.string.StringExtension

/**
 * Ready-made standard syntax: everything from [MathSyntax], boolean
 * logic and comparisons from [LogicExtension], and strings from
 * [StringExtension]. Registered in math-logic-string order so that the
 * arithmetic `+` is tried before the string-concat one.
 */
object StandardSyntax : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        MathSyntax.register(registry)
        LogicExtension.register(registry)
        StringExtension.register(registry)
    }
}
