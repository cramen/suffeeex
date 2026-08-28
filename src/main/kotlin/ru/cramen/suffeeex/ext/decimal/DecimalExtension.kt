package ru.cramen.suffeeex.ext.decimal

import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.node.CompareOp
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.NumericOp
import ru.cramen.suffeeex.core.node.StackCategory
import ru.cramen.suffeeex.core.node.TypeEmission
import ru.cramen.suffeeex.core.node.TypedNode
import ru.cramen.suffeeex.core.syntax.ExtensionRegistry
import ru.cramen.suffeeex.core.syntax.FunctionParser
import ru.cramen.suffeeex.core.syntax.InfixOperatorParser
import ru.cramen.suffeeex.core.syntax.LiteralParser
import ru.cramen.suffeeex.core.syntax.PrefixOperatorParser
import ru.cramen.suffeeex.core.syntax.SyntaxExtension
import ru.cramen.suffeeex.core.token.HIGH_TOKEN_PRIORITY
import ru.cramen.suffeeex.core.token.RegexpTokenParser
import ru.cramen.suffeeex.core.token.Token
import ru.cramen.suffeeex.core.token.TokenType
import ru.cramen.suffeeex.ext.logic.EqualsTokenType
import ru.cramen.suffeeex.ext.logic.GreaterOrEqualTokenType
import ru.cramen.suffeeex.ext.logic.GreaterTokenType
import ru.cramen.suffeeex.ext.logic.LessOrEqualTokenType
import ru.cramen.suffeeex.ext.logic.LessTokenType
import ru.cramen.suffeeex.ext.logic.NotEqualsTokenType
import ru.cramen.suffeeex.ext.math.operator.DivideTokenType
import ru.cramen.suffeeex.ext.math.operator.MinusTokenType
import ru.cramen.suffeeex.ext.math.operator.MultiplyTokenType
import ru.cramen.suffeeex.ext.math.operator.NegationNode
import ru.cramen.suffeeex.ext.math.operator.PercentTokenType
import ru.cramen.suffeeex.ext.math.operator.PlusTokenType
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.reflect.KClass

object DecimalTokenType : TokenType()

/** BigDecimal literal: `123bd`, `1.5bd`. */
class DecimalLiteralNode(val value: BigDecimal) : TypedNode {
    override val type: KClass<*> = BigDecimal::class

    override fun build(): Expression = Expression { value }

    override fun emit(emission: Emission) = emission.constant(BigDecimal::class, value)
}

private val OP_SYMBOLS = mapOf(
    NumericOp.ADD to "+",
    NumericOp.SUB to "-",
    NumericOp.MUL to "*",
    NumericOp.DIV to "/",
    NumericOp.REM to "%",
)

private val OP_METHODS = mapOf(
    NumericOp.ADD to "add",
    NumericOp.SUB to "subtract",
    NumericOp.MUL to "multiply",
    NumericOp.DIV to "divide",
    NumericOp.REM to "remainder",
)

private val OP_FUNCTIONS: Map<NumericOp, (BigDecimal, BigDecimal) -> BigDecimal> = mapOf(
    NumericOp.ADD to BigDecimal::add,
    NumericOp.SUB to BigDecimal::subtract,
    NumericOp.MUL to BigDecimal::multiply,
    NumericOp.DIV to BigDecimal::divide,
    NumericOp.REM to BigDecimal::remainder,
)

/**
 * BigDecimal binary operation: both operands must be BigDecimal, the result
 * is BigDecimal. Division keeps raw `BigDecimal.divide(right)` semantics
 * under [RoundingMode.UNNECESSARY] (the exact quotient is returned;
 * non-terminating division like `1bd / 3bd` throws ArithmeticException at
 * runtime) and switches to `left.divide(right, roundingMode)` for any other
 * mode — note that overload rounds at the fixed scale
 * `left.scale() - right.scale()`. The other operators ignore the mode.
 * Division by zero always throws ArithmeticException, exactly like
 * BigDecimal itself.
 */
class DecimalArithmeticNode(
    val op: NumericOp,
    val left: TypedNode,
    val right: TypedNode,
    val roundingMode: RoundingMode = RoundingMode.UNNECESSARY,
) : TypedNode {
    override val type: KClass<*> = BigDecimal::class

    init {
        if (left.type != BigDecimal::class || right.type != BigDecimal::class) {
            throw ExpressionException(
                "operator '${OP_SYMBOLS.getValue(op)}' requires both operands to be BigDecimal," +
                    " got ${left.type.simpleName} and ${right.type.simpleName}"
            )
        }
    }

    override fun build(): Expression {
        val f: (BigDecimal, BigDecimal) -> BigDecimal =
            if (op == NumericOp.DIV && roundingMode != RoundingMode.UNNECESSARY) {
                { a, b -> a.divide(b, roundingMode) }
            } else {
                OP_FUNCTIONS.getValue(op)
            }
        val l = left.build()
        val r = right.build()
        return Expression { c -> f(l.eval(c) as BigDecimal, r.eval(c) as BigDecimal) }
    }

    override fun emit(emission: Emission) {
        emission.push(left)
        emission.push(right)
        if (op == NumericOp.DIV && roundingMode != RoundingMode.UNNECESSARY) {
            emission.getStaticField(RoundingMode::class, roundingMode.name, RoundingMode::class)
            emission.invokeVirtual(
                BigDecimal::class, "divide", listOf(BigDecimal::class, RoundingMode::class), BigDecimal::class
            )
        } else {
            emission.invokeVirtual(BigDecimal::class, OP_METHODS.getValue(op), listOf(BigDecimal::class), BigDecimal::class)
        }
    }
}

/** Unary minus on a BigDecimal operand (`negate()`). */
class DecimalNegationNode(val operand: TypedNode) : TypedNode {
    override val type: KClass<*> = BigDecimal::class

    init {
        if (operand.type != BigDecimal::class) {
            throw ExpressionException("unary '-' requires a BigDecimal operand, got ${operand.type.simpleName}")
        }
    }

    override fun build(): Expression {
        val e = operand.build()
        return Expression { c -> (e.eval(c) as BigDecimal).negate() }
    }

    override fun emit(emission: Emission) {
        emission.push(operand)
        emission.invokeVirtual(BigDecimal::class, "negate", emptyList(), BigDecimal::class)
    }
}

/**
 * BigDecimal comparison (`<`, `<=`, `>`, `>=`, `==`, `!=`) via `compareTo`,
 * result is Boolean. Note that `==` / `!=` follow `compareTo`, not
 * `BigDecimal.equals`: the scale is ignored, so `1.0bd == 1.00bd` is true.
 */
class DecimalComparisonNode(val op: CompareOp, val left: TypedNode, val right: TypedNode) : TypedNode {
    override val type: KClass<*> = Boolean::class

    init {
        if (left.type != BigDecimal::class || right.type != BigDecimal::class) {
            throw ExpressionException(
                "operator '${if (op == CompareOp.EQ) "==" else if (op == CompareOp.NE) "!=" else op.name}'" +
                    " requires both operands to be BigDecimal," +
                    " got ${left.type.simpleName} and ${right.type.simpleName}"
            )
        }
    }

    override fun build(): Expression {
        val l = left.build()
        val r = right.build()
        return Expression { c ->
            val cmp = (l.eval(c) as BigDecimal).compareTo(r.eval(c) as BigDecimal)
            when (op) {
                CompareOp.LT -> cmp < 0
                CompareOp.LE -> cmp <= 0
                CompareOp.GT -> cmp > 0
                CompareOp.GE -> cmp >= 0
                CompareOp.EQ -> cmp == 0
                CompareOp.NE -> cmp != 0
            }
        }
    }

    override fun emit(emission: Emission) {
        emission.push(left)
        emission.push(right)
        emission.invokeVirtual(BigDecimal::class, "compareTo", listOf(BigDecimal::class), Int::class)
        emission.constant(Int::class, 0)
        emission.compare(op, Int::class)
    }
}

private val TO_BIG_DECIMAL_SOURCES = setOf(Int::class, Long::class, Double::class, String::class)

/**
 * Conversion to BigDecimal. Int/Long go through `BigDecimal.valueOf(long)`,
 * Double through `BigDecimal.valueOf(double)` — string-exact semantics:
 * `toBigDecimal(0.1)` equals `BigDecimal("0.1")`, not the raw IEEE-754
 * expansion of the literal. Strings are parsed with the `BigDecimal(String)`
 * constructor. Other argument types are rejected at compile time.
 */
class ToBigDecimalNode(val arg: TypedNode) : TypedNode {
    override val type: KClass<*> = BigDecimal::class

    private val convert: (Any?) -> BigDecimal = when (arg.type) {
        Int::class, Long::class -> { v -> BigDecimal.valueOf((v as Number).toLong()) }
        Double::class -> { v -> BigDecimal.valueOf(v as Double) }
        String::class -> { v -> BigDecimal(v as String) }
        else -> throw ExpressionException(
            "function 'toBigDecimal' expects an Int, Long, Double or String argument, got ${arg.type.simpleName}"
        )
    }

    override fun build(): Expression {
        val e = arg.build()
        return Expression { c -> convert(e.eval(c)) }
    }

    override fun emit(emission: Emission) {
        when (arg.type) {
            Int::class -> {
                emission.push(arg)
                emission.convertNumeric(Int::class, Long::class)
                emission.invokeStatic(BigDecimal::class, "valueOf", listOf(Long::class), BigDecimal::class)
            }
            Long::class, Double::class -> {
                emission.push(arg)
                emission.invokeStatic(BigDecimal::class, "valueOf", listOf(arg.type), BigDecimal::class)
            }
            String::class -> {
                emission.newObject(BigDecimal::class)
                emission.push(arg)
                emission.invokeConstructor(BigDecimal::class, listOf(String::class))
            }
        }
    }
}

/**
 * BigDecimal instance-method invocation with statically known argument and
 * result types: `args[0]` is the receiver, [paramTypes] are the JVM
 * parameter types of the rest. The implementation is chosen once at compile
 * time. Covers `abs`/`min`/`max`/`pow`/`signum` and the `toInt`/`toLong`/
 * `toFloat`/`toDouble` primitive conversions.
 */
class DecimalMethodNode(
    val method: String,
    val args: List<TypedNode>,
    override val type: KClass<*>,
    private val paramTypes: List<KClass<*>>,
    private val impl: (List<Any?>) -> Any?,
) : TypedNode {
    override fun build(): Expression {
        val built = args.map { it.build() }
        return Expression { c -> impl(built.map { arg -> arg.eval(c) }) }
    }

    override fun emit(emission: Emission) {
        args.forEach { emission.push(it) }
        emission.invokeVirtual(BigDecimal::class, method, paramTypes, type)
    }
}

/** `x.setScale(scale, roundingMode)` with the extension's rounding mode. */
class SetScaleNode(val arg: TypedNode, val scale: TypedNode, val roundingMode: RoundingMode) : TypedNode {
    override val type: KClass<*> = BigDecimal::class

    init {
        if (arg.type != BigDecimal::class || scale.type != Int::class) {
            throw ExpressionException(
                "function 'setScale' expects a BigDecimal and an Int argument," +
                    " got ${arg.type.simpleName} and ${scale.type.simpleName}"
            )
        }
    }

    override fun build(): Expression {
        val a = arg.build()
        val s = scale.build()
        return Expression { c -> (a.eval(c) as BigDecimal).setScale(s.eval(c) as Int, roundingMode) }
    }

    override fun emit(emission: Emission) {
        emission.push(arg)
        emission.push(scale)
        emission.getStaticField(RoundingMode::class, roundingMode.name, RoundingMode::class)
        emission.invokeVirtual(BigDecimal::class, "setScale", listOf(Int::class, RoundingMode::class), BigDecimal::class)
    }
}

private fun infix(tokenType: TokenType, precedence: Int, compile: (TypedNode, TypedNode) -> TypedNode) =
    object : InfixOperatorParser {
        override val tokenType = tokenType
        override val precedence = precedence
        override fun compile(left: TypedNode, right: TypedNode): TypedNode = compile(left, right)
    }

/**
 * Function parser handling BigDecimal arguments itself and delegating any
 * other argument types to [delegate] — the math parser captured from the
 * registry before this one is registered under the same name.
 */
private class DecimalFunction(
    override val name: String,
    arity: Int,
    private val delegate: FunctionParser?,
    private val applies: (List<TypedNode>) -> Boolean,
    private val node: (List<TypedNode>) -> TypedNode,
) : FunctionParser {
    override val minArgs = arity
    override val maxArgs = arity

    override fun compile(args: List<TypedNode>): TypedNode =
        if (applies(args)) {
            node(args)
        } else {
            delegate?.compile(args)
                ?: throw ExpressionException(
                    "function '$name' does not accept arguments of type " +
                        args.joinToString(", ") { it.type.simpleName ?: "?" }
                )
        }
}

// toInt/toLong/toFloat/toDouble on a BigDecimal receiver, with truncation
// semantics like BigDecimal itself.
private fun decimalConversion(
    name: String,
    delegate: FunctionParser?,
    method: String,
    target: KClass<*>,
    impl: (BigDecimal) -> Any,
) = DecimalFunction(name, 1, delegate, { it[0].type == BigDecimal::class }, { args ->
    DecimalMethodNode(method, args, target, emptyList()) { v -> impl(v[0] as BigDecimal) }
})

private fun decimalMethod(
    name: String,
    arity: Int,
    delegate: FunctionParser?,
    applies: (List<TypedNode>) -> Boolean,
    method: String,
    paramTypes: List<KClass<*>>,
    resultType: KClass<*>,
    impl: (List<Any?>) -> Any?,
) = DecimalFunction(name, arity, delegate, applies, { args ->
    DecimalMethodNode(method, args, resultType, paramTypes, impl)
})

/**
 * BigDecimal support: `bd` literals, the five arithmetic operators, unary
 * minus, the six comparison operators, conversions (`toBigDecimal` and
 * `toInt`/`toLong`/`toFloat`/`toDouble` on decimals) and the decimal
 * functions `abs`/`min`/`max`/`pow`/`signum`/`setScale`.
 *
 * [roundingMode] is a setting of the extension: it applies to division and
 * `setScale` only. The default [RoundingMode.UNNECESSARY] keeps raw
 * BigDecimal semantics — division by zero and non-terminating division throw
 * ArithmeticException at runtime.
 *
 * Composes with the numeric extensions: every operator is an additional
 * parser on the existing math/logic token types, tried after the numeric
 * ones (first success wins), so Int/Double/etc. expressions are unaffected.
 * Two pieces REPLACE previously registered parsers and delegate to them for
 * non-BigDecimal operands, so registration order matters. Prefix operators
 * are single-per-token, so the unary minus registered here replaces the
 * arithmetic one and delegates to [NegationNode] — register this extension
 * after `ArithmeticExtension`. Function parsers are single-per-name, so the
 * `toInt`/`toLong`/`toFloat`/`toDouble`/`abs`/`min`/`max`/`pow` parsers
 * registered here delegate to the math ones for non-BigDecimal arguments —
 * register this extension after `MathFunctionsExtension` (without it, these
 * functions reject non-BigDecimal arguments at compile time).
 */
class DecimalExtension(private val roundingMode: RoundingMode = RoundingMode.UNNECESSARY) : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        // BigDecimal constants cannot go through LDC: construct them from their string form.
        // Scoped to this registry: other compilers keep the reference-type fallback.
        registry.registerTypeEmission(object : TypeEmission {
            override val type = BigDecimal::class
            override val descriptor = "Ljava/math/BigDecimal;"
            override val category = StackCategory.REFERENCE
            override val wrapperInternalName: String? = null
            override val unboxMethod: String? = null
            override fun pushConstant(emission: Emission, value: Any) {
                emission.newObject(BigDecimal::class)
                emission.ldc(value.toString())
                emission.invokeConstructor(BigDecimal::class, listOf(String::class))
            }
        })

        // HIGH priority: the number literal regex (MEDIUM) would otherwise grab
        // the digits of "1.5bd" and leave "bd" dangling.
        registry.registerTokenParser(
            RegexpTokenParser(DecimalTokenType, Regex("\\d+(\\.\\d+)?bd"), HIGH_TOKEN_PRIORITY)
        )
        registry.registerLiteral(object : LiteralParser {
            override val tokenType = DecimalTokenType

            override fun compile(token: Token, varTypes: Map<String, KClass<*>>): TypedNode =
                DecimalLiteralNode(BigDecimal(token.value.dropLast(2)))
        })

        registry.registerInfixOperator(infix(PlusTokenType, 10) { l, r -> DecimalArithmeticNode(NumericOp.ADD, l, r) })
        registry.registerInfixOperator(infix(MinusTokenType, 10) { l, r -> DecimalArithmeticNode(NumericOp.SUB, l, r) })
        registry.registerInfixOperator(
            infix(MultiplyTokenType, 20) { l, r -> DecimalArithmeticNode(NumericOp.MUL, l, r) }
        )
        registry.registerInfixOperator(
            infix(DivideTokenType, 20) { l, r -> DecimalArithmeticNode(NumericOp.DIV, l, r, roundingMode) }
        )
        registry.registerInfixOperator(infix(PercentTokenType, 20) { l, r -> DecimalArithmeticNode(NumericOp.REM, l, r) })

        registry.registerPrefixOperator(object : PrefixOperatorParser {
            override val tokenType = MinusTokenType
            override val precedence = 30
            override fun compile(operand: TypedNode): TypedNode =
                if (operand.type == BigDecimal::class) DecimalNegationNode(operand) else NegationNode(operand)
        })

        registry.registerInfixOperator(infix(LessTokenType, 7) { l, r -> DecimalComparisonNode(CompareOp.LT, l, r) })
        registry.registerInfixOperator(infix(LessOrEqualTokenType, 7) { l, r -> DecimalComparisonNode(CompareOp.LE, l, r) })
        registry.registerInfixOperator(infix(GreaterTokenType, 7) { l, r -> DecimalComparisonNode(CompareOp.GT, l, r) })
        registry.registerInfixOperator(
            infix(GreaterOrEqualTokenType, 7) { l, r -> DecimalComparisonNode(CompareOp.GE, l, r) }
        )
        registry.registerInfixOperator(infix(EqualsTokenType, 6) { l, r -> DecimalComparisonNode(CompareOp.EQ, l, r) })
        registry.registerInfixOperator(infix(NotEqualsTokenType, 6) { l, r -> DecimalComparisonNode(CompareOp.NE, l, r) })

        // Capture the math function parsers BEFORE overwriting them: function
        // parsers are single-per-name, and the decimal variants delegate to
        // these for non-BigDecimal arguments.
        val mathToInt = registry.function("toInt")
        val mathToLong = registry.function("toLong")
        val mathToFloat = registry.function("toFloat")
        val mathToDouble = registry.function("toDouble")
        val mathAbs = registry.function("abs")
        val mathMin = registry.function("min")
        val mathMax = registry.function("max")
        val mathPow = registry.function("pow")

        registry.registerFunction(
            DecimalFunction("toBigDecimal", 1, null, { it[0].type in TO_BIG_DECIMAL_SOURCES }, { ToBigDecimalNode(it[0]) })
        )
        registry.registerFunction(
            DecimalFunction(
                "setScale", 2, null,
                { it[0].type == BigDecimal::class && it[1].type == Int::class },
                { SetScaleNode(it[0], it[1], roundingMode) },
            )
        )

        registry.registerFunction(decimalConversion("toInt", mathToInt, "intValue", Int::class) { it.toInt() })
        registry.registerFunction(decimalConversion("toLong", mathToLong, "longValue", Long::class) { it.toLong() })
        registry.registerFunction(decimalConversion("toFloat", mathToFloat, "floatValue", Float::class) { it.toFloat() })
        registry.registerFunction(
            decimalConversion("toDouble", mathToDouble, "doubleValue", Double::class) { it.toDouble() }
        )

        registry.registerFunction(
            decimalMethod("abs", 1, mathAbs, { it[0].type == BigDecimal::class }, "abs", emptyList(), BigDecimal::class) { v ->
                (v[0] as BigDecimal).abs()
            }
        )
        registry.registerFunction(
            decimalMethod(
                "min", 2, mathMin, { a -> a.all { it.type == BigDecimal::class } },
                "min", listOf(BigDecimal::class), BigDecimal::class,
            ) { v -> (v[0] as BigDecimal).min(v[1] as BigDecimal) }
        )
        registry.registerFunction(
            decimalMethod(
                "max", 2, mathMax, { a -> a.all { it.type == BigDecimal::class } },
                "max", listOf(BigDecimal::class), BigDecimal::class,
            ) { v -> (v[0] as BigDecimal).max(v[1] as BigDecimal) }
        )
        registry.registerFunction(
            decimalMethod(
                "pow", 2, mathPow, { it[0].type == BigDecimal::class && it[1].type == Int::class },
                "pow", listOf(Int::class), BigDecimal::class,
            ) { v -> (v[0] as BigDecimal).pow(v[1] as Int) }
        )
        registry.registerFunction(
            decimalMethod("signum", 1, null, { it[0].type == BigDecimal::class }, "signum", emptyList(), Int::class) { v ->
                (v[0] as BigDecimal).signum()
            }
        )
    }
}
