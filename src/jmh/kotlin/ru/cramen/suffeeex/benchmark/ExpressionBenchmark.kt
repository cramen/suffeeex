package ru.cramen.suffeeex.benchmark

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import ru.cramen.suffeeex.core.EvaluationContext
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionCompiler
import ru.cramen.suffeeex.core.MapEvaluationContext
import ru.cramen.suffeeex.core.backend.CompositionBackend
import ru.cramen.suffeeex.core.backend.asm.AsmBackend
import ru.cramen.suffeeex.ext.math.MathSyntax
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.reflect.KClass

/**
 * Compares evaluation of compiled expressions with the identical
 * computation written natively in Kotlin, for both backends
 * (CompositionBackend and AsmBackend).
 *
 * Native twins mirror the extension semantics exactly:
 * - arithmetic: Long ops stay Long (see BinaryArithmeticNode);
 * - pow: (Double, Double) -> Double, sqrt: Double -> Double (MathFunctionsExtension);
 * - abs on Long input stays Long.
 *
 * Inputs are varied cheaply (counter masked into a small range) and results
 * are consumed by a Blackhole, so neither side can be optimized away.
 * The input update code is identical on both sides of each pair; the compiled
 * side additionally pushes the inputs into the context map, which is part of
 * realistic usage of a compiled expression.
 *
 * Each expression pair also has a Specialized twin: compiled once against a
 * fun interface (default AsmBackend) and invoked directly with the varying
 * input fields, without any evaluation context.
 */
fun interface ArithmeticOp {
    fun eval(a: Long, b: Long): Long
}

fun interface PythagorasOp {
    fun eval(a: Double, b: Double): Double
}

fun interface AbsOp {
    fun eval(x: Long): Long
}

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
// JMH requires the benchmark class to be non-final (Kotlin classes are final by default)
open class ExpressionBenchmark {

    private val compiler = ExpressionCompiler(MathSyntax)

    private val longVarTypes = mapOf<String, KClass<*>>("a" to Long::class, "b" to Long::class, "x" to Long::class)
    private val doubleVarTypes = mapOf<String, KClass<*>>("a" to Double::class, "b" to Double::class)

    private lateinit var arithmeticExprComposition: Expression
    private lateinit var arithmeticExprAsm: Expression
    private lateinit var pythagorasExprComposition: Expression
    private lateinit var pythagorasExprAsm: Expression
    private lateinit var absExprComposition: Expression
    private lateinit var absExprAsm: Expression
    private lateinit var constantExprComposition: Expression
    private lateinit var constantExprAsm: Expression
    private lateinit var arithmeticSpecialized: ArithmeticOp
    private lateinit var pythagorasSpecialized: PythagorasOp
    private lateinit var absSpecialized: AbsOp

    private var counter = 0L
    private var a = 0L
    private var b = 0L
    private var x = 0L

    // inputs for the constant-expression native twin (prevents constant folding)
    private var two = 2L
    private var three = 3L
    private var four = 4L

    private val values = mutableMapOf<String, Any?>(
        "a" to 0L, "b" to 0L, "x" to 0L,
    )
    private val context: EvaluationContext = MapEvaluationContext(values)

    // pythagoras variables are declared Double, so they need their own context
    private val doubleValues = mutableMapOf<String, Any?>(
        "a" to 0.0, "b" to 0.0,
    )
    private val doubleContext: EvaluationContext = MapEvaluationContext(doubleValues)

    @Setup
    fun compileExpressions() {
        arithmeticExprComposition = compiler.compile("2L + 3L * \$a - 10L / \$b", longVarTypes, CompositionBackend)
        arithmeticExprAsm = compiler.compile("2L + 3L * \$a - 10L / \$b", longVarTypes, AsmBackend)
        pythagorasExprComposition =
            compiler.compile("sqrt(pow(\$a, 2.0) + pow(\$b, 2.0))", doubleVarTypes, CompositionBackend)
        pythagorasExprAsm = compiler.compile("sqrt(pow(\$a, 2.0) + pow(\$b, 2.0))", doubleVarTypes, AsmBackend)
        absExprComposition = compiler.compile("abs(\$x - 5L) * 2L", longVarTypes, CompositionBackend)
        absExprAsm = compiler.compile("abs(\$x - 5L) * 2L", longVarTypes, AsmBackend)
        constantExprComposition = compiler.compile("2 + 3 * 4", backend = CompositionBackend)
        constantExprAsm = compiler.compile("2 + 3 * 4", backend = AsmBackend)
        arithmeticSpecialized = compiler.compile("2L + 3L * \$a - 10L / \$b", ArithmeticOp::class)
        pythagorasSpecialized = compiler.compile("sqrt(pow(\$a, 2.0) + pow(\$b, 2.0))", PythagorasOp::class)
        absSpecialized = compiler.compile("abs(\$x - 5L) * 2L", AbsOp::class)
    }

    private fun nextInputs() {
        val c = counter++
        a = 3 + (c and 0xF)
        b = 1 + (c and 0x7)
        x = c and 0xF
        values["a"] = a
        values["b"] = b
        values["x"] = x
        doubleValues["a"] = a.toDouble()
        doubleValues["b"] = b.toDouble()
    }

    private fun nextConstants() {
        val c = counter++
        two = 2 + (c and 1)
        three = 3 + (c and 1)
        four = 4 + (c and 1)
    }

    @Benchmark
    fun compiledArithmeticComposition(bh: Blackhole) {
        nextInputs()
        bh.consume(arithmeticExprComposition.eval(context))
    }

    @Benchmark
    fun compiledArithmeticAsm(bh: Blackhole) {
        nextInputs()
        bh.consume(arithmeticExprAsm.eval(context))
    }

    @Benchmark
    fun compiledArithmeticSpecialized(bh: Blackhole) {
        nextInputs()
        bh.consume(arithmeticSpecialized.eval(a, b))
    }

    @Benchmark
    fun nativeArithmetic(bh: Blackhole) {
        nextInputs()
        bh.consume(2L + 3L * a - 10L / b)
    }

    @Benchmark
    fun compiledPythagorasComposition(bh: Blackhole) {
        nextInputs()
        bh.consume(pythagorasExprComposition.eval(doubleContext))
    }

    @Benchmark
    fun compiledPythagorasAsm(bh: Blackhole) {
        nextInputs()
        bh.consume(pythagorasExprAsm.eval(doubleContext))
    }

    @Benchmark
    fun compiledPythagorasSpecialized(bh: Blackhole) {
        nextInputs()
        bh.consume(pythagorasSpecialized.eval(a.toDouble(), b.toDouble()))
    }

    @Benchmark
    fun nativePythagoras(bh: Blackhole) {
        nextInputs()
        bh.consume(sqrt(a.toDouble().pow(2.0) + b.toDouble().pow(2.0)))
    }

    @Benchmark
    fun compiledAbsComposition(bh: Blackhole) {
        nextInputs()
        bh.consume(absExprComposition.eval(context))
    }

    @Benchmark
    fun compiledAbsAsm(bh: Blackhole) {
        nextInputs()
        bh.consume(absExprAsm.eval(context))
    }

    @Benchmark
    fun compiledAbsSpecialized(bh: Blackhole) {
        nextInputs()
        bh.consume(absSpecialized.eval(x))
    }

    @Benchmark
    fun nativeAbs(bh: Blackhole) {
        nextInputs()
        bh.consume(abs(x - 5L) * 2L)
    }

    @Benchmark
    fun compiledConstantComposition(bh: Blackhole) {
        nextConstants()
        bh.consume(constantExprComposition.eval(context))
    }

    @Benchmark
    fun compiledConstantAsm(bh: Blackhole) {
        nextConstants()
        bh.consume(constantExprAsm.eval(context))
    }

    @Benchmark
    fun nativeConstant(bh: Blackhole) {
        nextConstants()
        bh.consume(two + three * four)
    }

    @Benchmark
    fun arithmeticReusedContextComposition(bh: Blackhole) {
        nextInputs()
        bh.consume(arithmeticExprComposition.eval(context))
    }

    @Benchmark
    fun arithmeticReusedContextAsm(bh: Blackhole) {
        nextInputs()
        bh.consume(arithmeticExprAsm.eval(context))
    }

    @Benchmark
    fun arithmeticFreshContextComposition(bh: Blackhole) {
        nextInputs()
        val fresh = MapEvaluationContext(mapOf("a" to a, "b" to b))
        bh.consume(arithmeticExprComposition.eval(fresh))
    }

    @Benchmark
    fun arithmeticFreshContextAsm(bh: Blackhole) {
        nextInputs()
        val fresh = MapEvaluationContext(mapOf("a" to a, "b" to b))
        bh.consume(arithmeticExprAsm.eval(fresh))
    }
}
