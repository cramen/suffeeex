# Changelog

The project is pre-1.0: the public API may change between minor versions.
Breaking changes are listed explicitly.

## Unreleased

- Number literals: exponent form (`1e3`, `1.5e-3` → Double, `1e3f` → Float),
  digit underscores (`1_000_000`), hex (`0xFF`) and binary (`0b101`) literals
  with the usual Int/Long typing rules.
- String functions: `matches` (whole-string regex), `startsWith`, `endsWith`,
  `indexOf`, `substring`, `replace` (literal), `toUpperCase`/`toLowerCase`
  (locale-independent), `trim`.

## 0.2.0

- Host functions bridge (`ext/host`): `ExtensionRegistry.registerHostFunction(name, KFunction)`
  and `HostFunctionsExtension(vararg pairs)` make user Kotlin/JVM functions
  callable from expressions, with compile-time signature checks. v1 supports
  top-level, `@JvmStatic`/Java static, and object/companion functions.
- Typed property access (`ext/property`): `$order.total` resolves the member
  at compile time (Kotlin property getter, `getX()`/`isX()`, public field),
  supports chains and interface-typed receivers. Built on the new
  `MemberAccessParser` extension point; `Emission` gained `invokeInterface`
  for interface receivers.
- Decimal extension (`ext/decimal`): BigDecimal `bd` literals, `+ - * / %`,
  unary minus, and all six comparisons (compareTo-based, so `==` ignores
  scale). Included in `StandardSyntax` — registered last, as its unary
  minus overrides arithmetic's. `DecimalExtension` is now a class taking an
  optional `RoundingMode` (default `UNNECESSARY` keeps raw semantics)
  applied to `/` and the new `setScale(x, scale)`. New decimal functions:
  `toBigDecimal` (Int/Long/Double/String; Double is string-exact), the
  truncating `toInt`/`toLong`/`toFloat`/`toDouble` on decimals,
  `abs`/`min`/`max`, `pow(x, n)` with Int `n`, and `signum` — the names
  shared with the math extension delegate to it for non-decimal arguments.
- Specialized compilation now accepts reference-typed parameters (e.g. a
  data class) on the target fun interface, not just primitives and String.
- Build: Gradle 8.9, Kotlin 1.9.24, vanniktech maven-publish 0.29.0 —
  publishing now uses the native Central Portal API.
- Compile cache is additionally bounded (LRU, 1024 entries per compiler),
  on top of soft-referenced values — unbounded expression sources can no
  longer accumulate cache keys.
- **Breaking (relative to pre-release `main`, not to any published
  version):** `ExpressionBackend.compile` / `SpecializedBackend.compile`
  take an additional `types: TypeEmissions` parameter (defaulted, so
  callers are source-compatible, but interface *implementers* must update
  their signatures). `TypeEmissions` is now a scoped instance registry
  (`ExtensionRegistry.typeEmissions`) instead of a global mutable object.
  `DecimalExtension` is now a class with an optional `RoundingMode`
  parameter — replace `DecimalExtension` with `DecimalExtension()` at
  call sites.

## 0.1.1

Identical code to 0.1.0; re-released because the 0.1.0 deployment got
stuck server-side in the Central Portal and blocked the coordinate.
(0.1.0 eventually published too — both versions exist on Maven Central.)

## 0.1.0

First public release.

- Statically typed expression compiler: source → typed node tree → backend.
- Backends: `AsmBackend` (default, ASM bytecode generation) and
  `CompositionBackend`; specialized compilation against a user fun
  interface (`compile(source, TargetInterface::class)`).
- Kotlin-style literal typing (Int/Long/Float/Double), no implicit numeric
  coercion, explicit `toInt`/`toLong`/`toFloat`/`toDouble`.
- Built-in extensions: math (operators, brackets, functions), logic
  (booleans, comparisons, `&&`/`||`/`!`, lazy `if`), strings, `$`
  variables; `MathSyntax` and `StandardSyntax` presets.
- Extensibility: `SyntaxExtension` extension points, `TypeEmissions` type
  registry, generic `Emission` primitives (labels, jumps, locals).
- Symbolic differentiation (`ext/calculus`).
