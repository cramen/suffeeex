package ru.cramen.suffeeex.ext.variable

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.LiteralParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.MEDIUM_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import kotlin.reflect.KClass

object VariableTokenType : TokenType()

/** Variable with a type declared at compile time via `varTypes`. */
class VariableNode(val name: String, override val type: KClass<*>) : TypedNode {
    override fun build(): Expression = Expression { context ->
        val value = context.resolve(name)
            ?: throw ExpressionException("variable '$name' is not present in the evaluation context")
        if (!type.isInstance(value)) {
            throw ExpressionException(
                "variable '$name' must be ${type.simpleName}, got ${value.javaClass.simpleName}"
            )
        }
        value
    }

    override fun emit(emission: Emission) = emission.loadVariable(name, type)
}

object VariableExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        registry.registerTokenParser(
            RegexpTokenParser(
                VariableTokenType,
                Regex("\\$[a-zA-Z_][a-zA-Z0-9_]*"),
                MEDIUM_TOKEN_PRIORITY,
            )
        )

        registry.registerLiteral(object : LiteralParser {
            override val tokenType = VariableTokenType
            override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode {
                val name = token.value.substring(1)
                val type = varTypes[name] ?: throw ExpressionException("unknown variable: $name")
                return VariableNode(name, type)
            }
        })
    }
}
