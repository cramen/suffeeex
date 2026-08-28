package ru.cramen.suffeeex.ext.property

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.MemberAccessParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.LOW_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.SimpleTokenParser
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter

object DotTokenType : TokenType()

/** A member resolved at compile time: a getter method or a public field. */
sealed interface PropertyAccessor {
    val name: String
    val type: KClass<*>
    fun read(target: Any): Any?
    fun emit(emission: Emission, owner: KClass<*>, type: KClass<*>)

    class Getter(val method: Method) : PropertyAccessor {
        override val name = method.name
        override val type = method.returnType.kotlin
        override fun read(target: Any): Any? = method.invoke(target)
        override fun emit(emission: Emission, owner: KClass<*>, type: KClass<*>) {
            // an interface receiver needs INVOKEINTERFACE, not INVOKEVIRTUAL
            if (owner.java.isInterface) {
                emission.invokeInterface(owner, method.name, emptyList(), type)
            } else {
                emission.invokeVirtual(owner, method.name, emptyList(), type)
            }
        }
    }

    class JavaField(val field: Field) : PropertyAccessor {
        override val name = field.name
        override val type = field.type.kotlin
        override fun read(target: Any): Any? = field.get(target)
        override fun emit(emission: Emission, owner: KClass<*>, type: KClass<*>) {
            emission.getField(owner, field.name, type)
        }
    }
}

/** Typed property read: `$order.total`. The accessor is resolved once at compile time. */
class PropertyNode(val target: TypedNode, val accessor: PropertyAccessor, override val type: KClass<*>) : TypedNode {
    override fun build(): Expression {
        val t = target.build()
        return Expression { context -> accessor.read(t.eval(context) as Any) }
    }

    override fun emit(emission: Emission) {
        emission.push(target)
        accessor.emit(emission, target.type, type)
    }
}

private fun resolveAccessor(targetType: KClass<*>, member: String): PropertyAccessor {
    // Kotlin member property first: its getter name may differ from getX()
    targetType.memberProperties.firstOrNull { it.name == member }?.javaGetter
        ?.let { return PropertyAccessor.Getter(it) }
    val capitalized = member.replaceFirstChar { it.uppercase() }
    targetType.java.methods.firstOrNull {
        it.parameterCount == 0 && (it.name == "get$capitalized" || it.name == "is$capitalized")
    }?.let { return PropertyAccessor.Getter(it) }
    targetType.java.fields.firstOrNull { it.name == member }
        ?.let { return PropertyAccessor.JavaField(it) }
    throw ExpressionException(
        "unknown property '$member' on ${targetType.simpleName}:" +
            " available properties are ${availableProperties(targetType)}"
    )
}

private fun availableProperties(targetType: KClass<*>): String {
    val names = sortedSetOf<String>()
    names += targetType.memberProperties.map { it.name }
    for (method in targetType.java.methods) {
        if (method.parameterCount != 0) continue
        when {
            method.name.startsWith("get") && method.name.length > 3 ->
                names += method.name.substring(3).replaceFirstChar { it.lowercase() }
            method.name.startsWith("is") && method.name.length > 2 ->
                names += method.name.substring(2).replaceFirstChar { it.lowercase() }
        }
    }
    names += targetType.java.fields.map { it.name }
    return names.joinToString()
}

object PropertyAccessExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        // LOW priority: the number literal regex (MEDIUM) must win for "1.5"
        registry.registerTokenParser(SimpleTokenParser(DotTokenType, ".", LOW_TOKEN_PRIORITY))

        registry.registerMemberAccess(object : MemberAccessParser {
            override val tokenType = DotTokenType
            override fun compile(target: TypedNode, member: Token): TypedNode {
                if (target.type.java.isPrimitive) {
                    throw ExpressionException(
                        "property access requires a reference type, got ${target.type.simpleName}"
                    )
                }
                val accessor = resolveAccessor(target.type, member.value)
                return PropertyNode(target, accessor, accessor.type)
            }
        })
    }
}
