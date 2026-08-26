package ru.cramen.suffeeex.ext.math

import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.ext.math.bracket.BracketExtension
import ru.cramen.suffeeex.ext.math.function.MathFunctionsExtension
import ru.cramen.suffeeex.ext.math.number.NumberExtension
import ru.cramen.suffeeex.ext.math.operator.ArithmeticExtension
import ru.cramen.suffeeex.ext.variable.VariableExtension

/**
 * Ready-made math syntax: numbers, arithmetic operators, brackets,
 * math functions and `$name` variables from the evaluation context.
 */
object MathSyntax : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        NumberExtension.register(registry)
        ArithmeticExtension.register(registry)
        BracketExtension.register(registry)
        MathFunctionsExtension.register(registry)
        VariableExtension.register(registry)
    }
}
