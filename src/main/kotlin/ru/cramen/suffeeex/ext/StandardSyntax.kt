package ru.cramen.suffeeex.ext

import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.ext.decimal.DecimalExtension
import ru.cramen.suffeeex.ext.logic.LogicExtension
import ru.cramen.suffeeex.ext.math.MathSyntax
import ru.cramen.suffeeex.ext.string.StringExtension

/**
 * Ready-made standard syntax: everything from [MathSyntax], boolean
 * logic and comparisons from [LogicExtension], strings from
 * [StringExtension], and decimals from [DecimalExtension]. Registered in
 * math-logic-string-decimal order so that the arithmetic `+` is tried
 * before the string-concat one, and [DecimalExtension] comes last: its
 * unary minus replaces the arithmetic one, and its decimal function
 * parsers replace the math ones, both delegating for non-decimals.
 */
object StandardSyntax : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        MathSyntax.register(registry)
        LogicExtension.register(registry)
        StringExtension.register(registry)
        DecimalExtension().register(registry)
    }
}
