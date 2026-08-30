# suffeeex

[![CI](https://github.com/cramen/suffeeex/actions/workflows/ci.yml/badge.svg)](https://github.com/cramen/suffeeex/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.cramen/suffeeex)](https://central.sonatype.com/artifact/io.github.cramen/suffeeex)

**super fast extensible expression executor framework**

A Kotlin/JVM library that parses expression strings into ready-to-run,
statically typed code. Compiled expressions evaluate at (or very near)
native Kotlin speed — and every piece of syntax, from arithmetic to `if`,
is an extension you can replace or augment.

```kotlin
val compiler = ExpressionCompiler(StandardSyntax)

val expr = compiler.compile(
    "if($amount > 100, toDouble($amount) * 0.9, toDouble($amount))",
    varTypes = mapOf("amount" to Int::class),
)
expr.eval(MapEvaluationContext(mapOf("amount" to 250)))   // 225.0
```

## Why

- **Fast by construction.** Parsing produces a typed node tree that a
  backend turns into executable code. The default backend generates JVM
  bytecode (via ASM): arithmetic runs on primitives, booleans short-circuit
  with real jumps, string concatenation uses `invokedynamic` — there is no
  interpreter loop and no AST walking at runtime.
- **Statically typed at parse time.** Literals follow Kotlin rules
  (`123` → Int, `123L` → Long, `1.5` → Double, `1.5f` → Float). Operations
  never coerce types: `1 + 1L` is a *compile* error; convert explicitly with
  `toLong(1) + 1L`. Type errors surface when you compile, not in production.
- **Extensible down to the syntax.** Numbers, operators, brackets,
  functions, variables, booleans, strings, `if` — all are ordinary
  extensions built on public extension points. New types plug into the
  `TypeEmissions` registry, and generic emission primitives (labels,
  jumps, local slots) let extensions add their own control flow — even
  loops — without touching the core.
- **Replaceable backend.** The frontend never sees bytecode. Nodes compile
  through a small `Emission` abstraction; the library ships a bytecode
  backend (`AsmBackend`, the default) and a fallback composition backend.
- **Symbolic transformations.** The typed tree is public API: parse without
  compiling (`parseTree`), transform, compile (`compileTree`). Ships with
  symbolic differentiation (`ext/calculus`) as a worked example.

## Performance

JMH benchmark (`./gradlew jmh`), average time per single evaluation, ns/op
(lower is better). "Native" is the same computation written in plain Kotlin.
"Specialized" compiles the expression against a user fun interface, so
variables arrive as plain method parameters (see below).

The absolute numbers below come from one specific machine (Apple Silicon,
arm64, JDK 21) and will differ on yours — what transfers across machines
is the *ratio* between the columns, not the nanoseconds. Run
`./gradlew jmh` locally for your own baseline.

| Expression | Composition | ASM (default) | Specialized | Native |
|---|---:|---:|---:|---:|
| `2L + 3L*$a - 10L/$b` | 64.6 | 32.2 | **28.9** | 27.1 |
| `abs($x - 5L) * 2L` | 66.4 | 30.6 | **27.5** | 27.2 |
| `sqrt(pow($a,2.0)+pow($b,2.0))` | 127.9 | 32.8 | 35.2 | 27.4 |
| `2 + 3 * 4` (no variables) | 7.6 | 0.86 | — | 0.86 |
| `$order.total * 2.0` (property access) | 24.9 | 12.8 | **10.5** | 13.8 |
| `vat($price, 0.2)` (host function) | 130.1 | 14.2 | **10.4** | 10.1 |

Bytecode generation is 2–4× faster than the composition backend and lands
within a few percent of hand-written Kotlin; the specialized variant is
statistically indistinguishable from native on integer arithmetic. Creating
a fresh `MapEvaluationContext` per call costs ~30 ns — reuse contexts in hot
paths, or use specialized compilation and skip the context entirely.
Property getters and static host functions are emitted as plain
`INVOKEVIRTUAL`/`INVOKESTATIC`, so on the ASM backend they run at native
speed (specialized is within noise of native); on the composition backend a
host call goes through reflective `KFunction.call`, which dominates its
~130 ns.

## Getting it

From Maven Central:

```kotlin
implementation("io.github.cramen:suffeeex:0.3.0")   // Gradle (Kotlin DSL)
```

```groovy
implementation 'io.github.cramen:suffeeex:0.3.0'    // Gradle (Groovy)
```

```xml
<dependency>
  <groupId>io.github.cramen</groupId>
  <artifactId>suffeeex</artifactId>
  <version>0.3.0</version>
</dependency>
```

Building from source still works:

```bash
git clone https://github.com/cramen/suffeeex && cd suffeeex
./gradlew build        # produces build/libs/suffeeex-0.3.0.jar
```

Requires a JDK 17+ runtime.

## Using the library

### Basic evaluation

```kotlin
val compiler = ExpressionCompiler(StandardSyntax)   // all built-in syntax

val expr = compiler.compile("2 + 3 * 4")
expr.eval(MapEvaluationContext(emptyMap()))   // 14 (Int)
```

Compile once, evaluate many times — compilation is the expensive step,
evaluation is the fast one.

### Variables

Variables are `$name`, read from an `EvaluationContext`. Their types must be
declared at compile time; an undeclared variable is a compile error:

```kotlin
val expr = compiler.compile(
    "$price * toDouble($count) * (1.0 - $discount)",
    varTypes = mapOf("price" to Double::class, "count" to Int::class, "discount" to Double::class),
)
expr.eval(MapEvaluationContext(mapOf("price" to 9.99, "count" to 3, "discount" to 0.1)))
```

(Note the static typing: `count` is Int while the rest is Double, so it is
converted explicitly — mixed-type arithmetic is a compile error.)

### Specialized compilation (fastest)

Declare a fun interface whose parameters are the expression's variables,
and compile straight into it:

```kotlin
fun interface Shipping {
    fun cost(weight: Double, distance: Long): Double
}

val shipping = compiler.compile(
    "5.0 + $weight * 0.1 + toDouble($distance) * 0.001",
    Shipping::class,
)
shipping.cost(12.5, 800)   // primitive in, primitive out — no map, no boxing
```

Parameter names must exactly cover the variables used; parameter and return
types are checked against the expression type at compile time (a wrapper or
nullable return type is accepted). Parameters may be primitives, String, or
any reference type (e.g. a data class — see property access below). The
generated implementation reads arguments from JVM parameter slots directly —
this is the variant that reaches native performance.

### Host functions

Register any Kotlin/JVM function and call it from expressions
(`ext/host`):

```kotlin
object Pricing {
    @JvmStatic
    fun vat(amount: Double): Double = amount * 0.2
}

val compiler = ExpressionCompiler(
    StandardSyntax,
    HostFunctionsExtension("vat" to Pricing::vat),   // or: "vat" to ::topLevelVat
)
compiler.compile("vat($price)", varTypes = mapOf("price" to Double::class))
```

The signature fixes the types: argument count and types and the return type
are checked at compile time. Both backends call the resolved JVM method
directly (no per-call reflection on the ASM backend). You can also register
functions piecemeal via `ExtensionRegistry.registerHostFunction(name, fn)`.

Version 1 supports top-level functions, `@JvmStatic`/Java static methods,
and object/companion members — instance methods of regular classes are
rejected at registration. Suspend, generic, vararg and default-argument
functions are not supported either.

### Property access

`ext/property` adds `$order.total`-style typed property access:

```kotlin
data class Customer(val name: String, val vip: Boolean)
data class Order(val total: Double, val customer: Customer)

val compiler = ExpressionCompiler(StandardSyntax, PropertyAccessExtension)
val expr = compiler.compile(
    "if($order.customer.vip, $order.total * 0.9, $order.total)",
    varTypes = mapOf("order" to Order::class),
)
expr.eval(MapEvaluationContext(mapOf("order" to Order(42.5, Customer("ann", true)))))   // 38.25
```

The member is resolved once, at compile time: a Kotlin member property's
getter first, then `getX()`/`isX()`, then a public field; an unknown member
is a compile error listing the available properties. Chains work
(`$order.customer.name`), and interface-typed receivers are supported.
Because the target's `KClass` is known at compile time, property access
also works in specialized compilation.

### Input suggestions

The `suggest` package builds expression-input autocomplete on top of the
same extension registry that drives parsing:

```kotlin
import ru.cramen.suffeeex.suggest.suggest

val compiler = ExpressionCompiler(StandardSyntax)
compiler.suggest("co")   // cos(arg1), contains(arg1, arg2), ...
compiler.suggest("1 + ") // functions, variables, true/false, '(', '-', ...
```

`suggest(source, cursor = source.length, varTypes = emptyMap())` returns
`Suggestion(text, kind, detail)` items — functions with a synthetic
signature detail, variables from `varTypes` as `$name`, literal keywords,
operators, brackets and member access — filtered by the identifier fragment
being typed at the cursor (case-insensitively; case-exact prefix matches
rank first). After `$var.` with `var` declared in `varTypes`, the members of
its type are suggested instead, via the member access parser's
`suggestMembers` hook — `PropertyAccessExtension` implements it, so
`$order.` offers `total`, `customer`, etc. (a direct `$var.` receiver only;
chains are not resolved). Suggestions are registry-driven: a function or
operator registered by your own extension appears automatically, with no
extra wiring.

### Symbolic differentiation

The typed tree can be differentiated symbolically before compilation:
parse without compiling, differentiate, compile the result.

```kotlin
val tree = compiler.parseTree("sin($x) * $x", mapOf("x" to Double::class))
val derivative = Differentiator.differentiate(tree, "x")
val d = compiler.compileTree(derivative)
d.eval(MapEvaluationContext(mapOf("x" to 0.8)))   // sin(0.8) + 0.8·cos(0.8) ≈ 1.2747
```

`Differentiator` (`ru.cramen.suffeeex.ext.calculus`) applies the standard
rules — sum, product, quotient, chain — and simplifies the result:
constants are folded, trivial arithmetic (`0 + x`, `x * 1`, ...) is
collapsed. A derivative is an ordinary tree, so everything else works on
it too, including specialized compilation:

```kotlin
fun interface Derivative {
    fun eval(x: Double): Double
}

val f = AsmBackend.compile(derivative, Derivative::class) as Derivative
f.eval(0.8)   // same value, straight from parameter slots
```

Differentiation is Double-only: it covers `$` variables, `+ - * /`, unary
`-`, and `sqrt`/`pow`/`sin`/`cos`/`tan`/`ln`/`log10`/`exp`. Anything else —
`if`, `abs`, strings, `floor`/`round`, Int/Long arithmetic — raises an
`ExpressionException` at differentiation (compile) time. Your own
extensions become differentiable by implementing `DifferentiableNode`
(`differentiate(by: String): TypedNode`) on their node classes.

### Choosing a backend

```kotlin
compiler.compile("1 + 2")                                    // AsmBackend (default)
compiler.compile("1 + 2", backend = CompositionBackend)      // fallback: function composition
```

`CompositionBackend` builds a tree of Kotlin lambdas — slower at runtime,
but simple and dependency-free. Both backends share the frontend and the
static typing rules, so behavior (results and compile errors) is identical;
both are exercised by the same test suite.

Compilation results are cached per (source, variable types or target
interface, backend): compiling the same expression again returns the same
stateless instance. The cache is a bounded LRU (1024 entries per compiler)
of soft references — under memory pressure or past the bound an entry is
dropped and simply recompiled on demand, so even unbounded on-the-fly
expression sources cannot accumulate cache keys forever. In the ASM backend
every generated class is defined in its own classloader, so an expression
that is no longer referenced can be unloaded by the garbage collector —
dynamically compiled expressions do not leak metaspace.

## Built-in syntax (`StandardSyntax`)

### Literals

| Literal | Type |
|---|---|
| `123` | Int (Long on Int overflow) |
| `123L` | Long |
| `1.5` | Double |
| `1.5f`, `123f` | Float |
| `1e3`, `1.5e-3` | Double (`1e3f` → Float) |
| `1_000_000` | underscores ignored, typed as without them |
| `0xFF`, `0b101` | Int (Long on overflow; `0xFFL` → Long) |
| `1.5bd`, `123bd` | BigDecimal |
| `"text\n"` | String (escapes: `\n` `\t` `\"` `\\`) |
| `true`, `false` | Boolean |
| `$name` | variable (type declared at compile time) |

### Operators (precedence, higher binds tighter)

| Operators | Precedence | Types |
|---|---|---|
| unary `-`, `!` | 30 | numeric / Boolean |
| `*` `/` `%` | 20 | same-type numeric |
| `+` `-` | 10 | same-type numeric; `+` also String concat |
| `<` `<=` `>` `>=` | 7 | same-type numeric → Boolean |
| `==` `!=` | 6 | numeric, String, Boolean (same type) → Boolean |
| `&&` | 5 | Boolean, short-circuiting |
| `||` | 4 | Boolean, short-circuiting |

Brackets `(` `)` group as usual. No implicit conversions anywhere:
`1 + 1L`, `"a" < "b"`, `if(1, 2, 3)` are all compile errors.

### Functions

- Conversions: `toInt(x)`, `toLong(x)`, `toFloat(x)`, `toDouble(x)` — any
  numeric argument.
- Typed (result keeps the argument type): `abs(x)`, `min(a, b)`,
  `max(a, b)` — Int/Long/Float/Double, all args same type.
- Double-only: `sqrt`, `pow(a, b)`, `sin`, `cos`, `tan`, `ln`, `log10`,
  `exp`, `floor`, `ceil`, `round` (ties to even, like `kotlin.math.round`).
- Strings: `length(s)` → Int, `contains(s, sub)`, `startsWith(s, prefix)`,
  `endsWith(s, suffix)` → Boolean, `indexOf(s, sub)` → Int,
  `substring(s, begin[, end])`, `replace(s, from, to)` (literal, not regex),
  `toUpperCase(s)`, `toLowerCase(s)` (locale-independent, `Locale.ROOT`),
  `trim(s)`, `matches(s, pattern)` — whole-string regex match.
- Decimals (`bd` literals): `+ - * / %`, unary `-`, and all six
  comparisons on BigDecimal operands; `==`/`!=` follow `compareTo`, not
  `equals` — `1.0bd == 1.00bd` is true. Functions: `toBigDecimal(x)` from
  Int/Long/Double (string-exact: `toBigDecimal(0.1)` is
  `BigDecimal("0.1")`, not the raw IEEE-754 expansion) or String;
  `toInt`/`toLong`/`toFloat`/`toDouble` on decimals (truncating); `abs`,
  `min`, `max`, `pow(x, n)` (Int `n`), `signum(x)` → Int,
  `setScale(x, scale)`. `DecimalExtension` takes an extension-level
  `RoundingMode` applying to `/` and `setScale`: the default
  `RoundingMode.UNNECESSARY` keeps raw `java.math.BigDecimal` semantics —
  division by zero and non-terminating division (`1bd / 3bd`) throw
  `ArithmeticException` at runtime. Pick another mode when adding the
  extension: `DecimalExtension(RoundingMode.HALF_UP)` makes
  `1.00bd / 3bd` evaluate to `0.33bd` and `setScale(1.005bd, 2)` to
  `1.01bd`.
- Conditional: `if(condition, ifTrue, ifFalse)` — condition must be
  Boolean, branches must share a type; only the chosen branch is evaluated.

```kotlin
compiler.compile("if(contains($s, \"x\"), length($s), -1)",
                 varTypes = mapOf("s" to String::class))
```

### Picking syntax à la carte

`StandardSyntax` is just a preset. Compose your own from individual
extensions:

```kotlin
val compiler = ExpressionCompiler(
    NumberExtension,          // built-in extensions are objects
                              // (DecimalExtension is a class — see below)
    ArithmeticExtension,
    BracketExtension,
    MathFunctionsExtension,
    LogicExtension,
    StringExtension,
    DecimalExtension(),       // must come after ArithmeticExtension and
                              // MathFunctionsExtension: its unary minus
                              // and decimal function parsers replace the
                              // math ones and delegate for non-decimals
    VariableExtension,
)
```

Order matters when two extensions claim the same token: infix parsers are
tried in registration order (that is why `ArithmeticExtension` precedes
`StringExtension` — numeric `+` wins, strings fall through to concat),
prefix parsers are single-per-token (that is why `DecimalExtension` must
follow `ArithmeticExtension`), and function parsers are single-per-name
(that is why it must also follow `MathFunctionsExtension` — the decimal
variants of `toInt`/`abs`/`min`/... delegate to the math ones).

## Writing your own extension

An extension is anything implementing `SyntaxExtension` that registers its
pieces into the `ExtensionRegistry`. The pipeline:

```
source --Tokenizer--> tokens --SyntaxParser--> TypedNode tree --backend--> Expression
```

You can hook in at three levels:

1. **Tokens** — `TokenParser` (`SimpleTokenParser`, `SimpleMultiTokenParser`,
   `RegexpTokenParser`, or your own), with a priority: higher priority wins,
   longest match wins within a priority
   (`LOW_TOKEN_PRIORITY` 0 / `MEDIUM` 500 / `HIGH` 1000).
2. **Syntax** — implement one or more of:
   - `LiteralParser` — token → node (literals, variables)
   - `PrefixOperatorParser` / `InfixOperatorParser` — with precedence and
     associativity; several infix parsers may share one token type, the
     first whose `compile` succeeds wins
   - `FunctionParser` — `name(args...)` syntax with arity checking
   - `BracketParser` — the bracket pair used for grouping and call args
3. **Nodes** — a `TypedNode` knows its static `type` and implements two
   hooks: `build()` for the composition backend (plain Kotlin lambdas) and
   `emit(emission)` for bytecode backends (`Emission` offers constants,
   variable loads, numeric ops, comparisons, branching, short-circuit
   logic, string ops, math calls — no ASM knowledge required). Beyond the
   built-ins, `Emission` exposes generic primitives — `ldc`,
   `newObject`/`invokeConstructor`, `invokeStatic`/`invokeVirtual`,
   labels with conditional jumps, local slots
   (`newLocal`/`loadLocal`/`storeLocal`), `pop` — so custom control flow
   (even loops) and custom types are expressible without touching core.
   New types plug in via the `TypeEmissions` registry: a `TypeEmission`
   describes the JVM descriptor, stack category, boxing and constant
   pushing, and unregistered reference types get an automatic fallback.
   The built-in `compare` and arithmetic emissions cover primitive types;
   reference types express their operations via `invokeVirtual` /
   `invokeStatic` (see the BigDecimal example).
   See `src/test/kotlin/ru/cramen/suffeeex/extensibility/` for complete
   worked examples — a loop extension and a BigDecimal type extension.

A complete example — a `**` power operator on Doubles:

```kotlin
object PowTokenType : TokenType()

class PowNode(private val left: TypedNode, private val right: TypedNode) : TypedNode {
    override val type = Double::class

    override fun build(): Expression {
        val l = left.build()
        val r = right.build()
        return Expression { c -> (l.eval(c) as Double).pow(r.eval(c) as Double) }
    }

    override fun emit(emission: Emission) {
        emission.push(left)
        emission.push(right)
        emission.invokeMath("pow", listOf(Double::class, Double::class), Double::class)
    }
}

object PowExtension : SyntaxExtension {
    override fun register(registry: ExtensionRegistry) {
        registry.registerTokenParser(SimpleTokenParser(PowTokenType, "**", HIGH_TOKEN_PRIORITY))
        registry.registerInfixOperator(object : InfixOperatorParser {
            override val tokenType = PowTokenType
            override val precedence = 25            // binds tighter than * / %
            override fun compile(left: TypedNode, right: TypedNode): TypedNode {
                if (left.type != Double::class || right.type != Double::class)
                    throw ExpressionException("operator '**' expects Double operands")
                return PowNode(left, right)
            }
        })
    }
}

val compiler = ExpressionCompiler(StandardSyntax, PowExtension)
compiler.compile("2.0 ** 10.0").eval(MapEvaluationContext(emptyMap()))   // 1024.0
```

That's the whole deal: a token, a parser, a node. Both backends pick it up
without further work — and so does specialized compilation.

## Project layout

```
core/            Expression, EvaluationContext, ExpressionCompiler
core/token/      tokenizer + TokenParser implementations
core/syntax/     Pratt parser, ExtensionRegistry, extension points
core/node/       TypedNode, DifferentiableNode, Emission (backend-agnostic
                 bytecode IR), TypeEmission type registry
core/backend/    ExpressionBackend, SpecializedBackend, CompositionBackend,
                 asm/AsmBackend (default)
ext/math/        number / operator / bracket / function + MathSyntax preset
ext/logic/       booleans, comparisons, && || !, if()
ext/string/      string literals, + concat, length, contains
ext/decimal/     BigDecimal literals (bd), operators, conversions, functions
ext/host/        user Kotlin/JVM functions callable from expressions
ext/property/    typed property access ($order.total)
ext/variable/    $name variables
ext/calculus/    symbolic differentiation (Differentiator)
ext/StandardSyntax.kt   math + logic + string + decimal in one preset
src/jmh/         benchmarks
```

## Development

```bash
./gradlew build     # build + tests
./gradlew test      # tests (every extension is tested on both backends)
./gradlew jmh       # benchmarks -> build/results/jmh/results.txt
```

Kotlin 1.9.24, JVM target 17 (via a Gradle toolchain, so compilation is
independent of the launcher JDK). Use the Gradle wrapper (8.9) — Gradle 9.x
is incompatible with the Kotlin 1.9.x plugin. The Gradle daemon itself must
run on JDK 21 or older (kapt does not support newer JDKs); the compiled
library targets JVM 17 and runs on any JDK 17+.

## License

See [LICENSE](LICENSE).
