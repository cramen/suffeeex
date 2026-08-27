# suffeeex

**super fast extensible expression executor framework** (in development)

A Kotlin/JVM library for parsing and evaluating expressions.

## Core design ideas

- **Parsing produces a ready composition of functions**, not an AST to be
  interpreted at runtime. Evaluation executes this composition directly, so
  after JIT warmup it should perform comparably to native compiled code.
  Keep the evaluation path free of per-call allocation, reflection and
  dispatch overhead.
- **Everything is an extension.** All operations and functions usable in
  expressions (arithmetic, logic, and eventually conditions and even loops)
  are implemented as extensions of the library, not hardcoded in the core.
  The core only provides the extension points. New syntax must be addable
  without modifying core code.

## Tech stack

- Kotlin 1.9.10, JVM target 17, Gradle (Kotlin DSL), `java-library` +
  `maven-publish`
- Group `ru.cramen`, root package `ru.cramen.suffeeex`
- Tests: `kotlin("test")` on JUnit platform + Kotest assertions
  (`io.kotest:kotest-assertions-core-jvm`)
- Bytecode generation: ASM 9.7 (`org.ow2.asm:asm`) for the AsmBackend

## Commands

- Build: `./gradlew build`
- Test: `./gradlew test`
- Benchmark: `./gradlew jmh` — JMH benchmarks in
  `src/jmh/kotlin/ru/cramen/suffeeex/benchmark/`; results land in
  `build/results/jmh/results.txt` (and stdout). Short run configured:
  fork=1, 3 warmup + 5 measurement iterations.

Build notes:

- Use the wrapper (`./gradlew`, Gradle 7.5.1). Newer system Gradle (9.x)
  fails against the Kotlin 1.9.10 plugin.
- The build must RUN on JDK 21 or older: kapt (Kotlin 1.9.10) cannot parse
  `java.version` "22+" and fails with a bare `IllegalArgumentException: 25`.
  If the default JVM is newer, point Gradle at an older JDK, e.g.
  `./gradlew test -Dorg.gradle.java.home=/path/to/jdk-21` (or set
  `org.gradle.java.home` in `~/.gradle/gradle.properties`, or JAVA_HOME).
- A JVM target mismatch warning between `compileTestJava` (21) and
  `compileTestKotlin` (17) is currently tolerated; consider a JVM toolchain
  if it becomes an error.

## Current state and layout

The core frontend parses into a statically typed node tree; backends turn it
into an executable `Expression`. Numeric literals are typed at parse time
like Kotlin (`123` → Int, Long on Int overflow; `123L` → Long; `1.5` →
Double; `1.5f`/`123f` → Float). Operations never coerce: mixed-type operands
are a compile error; use `toInt`/`toLong`/`toFloat`/`toDouble` to convert.

- `core/Expression.kt` — `Expression` (fun interface, `eval(context)`),
  `EvaluationContext`, `MapEvaluationContext`, `ExpressionException`.
- `core/node/` — `TypedNode` (typed tree node: `type`, `build()` for the
  composition backend, `emit(emission)` hook for bytecode backends),
  `Emission` (bytecode abstraction owned by core, no ASM dependency),
  `NumericOp`, `CompareOp`. Besides numeric ops, `Emission` covers
  Boolean (`int` 0/1 on the stack) and String primitives: `compare`,
  `branch`, `logicalAnd`/`logicalOr`/`logicalNot`, `stringConcat`,
  `objectsEquals`, `invokeStringMethod`. Reference `!=` is the node's job
  (invert `objectsEquals` with `logicalNot`). Generic escape hatches let
  extensions add types and control flow without touching core: `ldc`,
  `newObject`/`invokeConstructor`, `invokeStatic`/`invokeVirtual`, `pop`,
  labels (`newLabel`/`mark`/`jump`/`jumpIfFalse`/`jumpIfTrue` over the
  `EmissionLabel` marker) and locals (`newLocal`/`loadLocal`/`storeLocal`).
- `core/node/TypeEmission.kt` — the type registry: `TypeEmission`
  (descriptor, `StackCategory`, wrapper/unbox, `pushConstant`) and
  `TypeEmissions` — an instance registry scoped by construction:
  `TypeEmissions(parent)` resolves own registrations, then the parent's,
  then an automatic reference-type fallback for unregistered types.
  `TypeEmissions.DEFAULT` (companion) holds the pre-registered
  Int/Long/Float/Double/Boolean/String. Each `ExtensionRegistry` owns a
  `TypeEmissions(DEFAULT)` (`typeEmissions`; extensions add types via
  `registerTypeEmission`), so one compiler's types never leak into
  another. All bytecode type knowledge (descriptors, boxing,
  load/store/return opcodes) derives from this registry — the numeric-op
  offset equals `StackCategory.ordinal`.
- `core/node/DifferentiableNode.kt` — symbolic differentiation hook:
  nodes implement `DifferentiableNode.differentiate(by)`; the rules live
  in the node classes, so new extensions implement it to become
  differentiable (`differentiateOrThrow` errors clearly otherwise).
- `core/backend/` — `ExpressionBackend.compile(root: TypedNode, types:
  TypeEmissions = TypeEmissions.DEFAULT)`, `CompositionBackend`
  (`root.build()`, ignores `types`), and `asm/AsmBackend` (generates a
  bytecode class per expression via `emit(emission)`, each defined in its
  own child classloader so it unloads with the expression). The default
  backend is `AsmBackend`. `SpecializedBackend` (`compile(root, target:
  KClass<*>, types = DEFAULT)`, implemented by both backends) compiles
  against a user fun interface whose single abstract method's parameters
  are the variables — direct parameter loads in ASM, a `Proxy` over an
  index-based context in composition.
- `core/token/` — tokenizer + token parsers (`TokenParser`,
  `SimpleTokenParser`, `SimpleMultiTokenParser`, `RegexpTokenParser`,
  priorities in `Priorities.kt`).
- `core/syntax/` — Pratt parser engine (`SyntaxParser`, produces
  `TypedNode`), `ExtensionRegistry` and the extension points:
  `LiteralParser`, `PrefixOperatorParser`, `InfixOperatorParser`,
  `FunctionParser`, `BracketParser`; a `SyntaxExtension` registers its
  pieces into the registry. Several `InfixOperatorParser`s may share one
  token: the first whose `compile` succeeds wins (precedence of the first
  registered), and if all fail the first exception is rethrown.
- `core/ExpressionCompiler.kt` — `ExpressionCompiler(vararg extensions)`;
  `compile(source, varTypes = emptyMap(), backend = AsmBackend)`
  returns a ready `Expression`. `$name` variable types
  come from `varTypes`; undeclared variables are a compile error.
  `compile(source, target: KClass<T>, backend)` is the specialized variant
  returning a `T` implementing the target fun interface; it validates the
  interface (exactly one abstract method, parameter names cover exactly the
  variables used, return type matches, wrappers accepted). Both `compile`
  variants are cached (`ConcurrentHashMap` of `SoftReference` keyed by
  source + varTypes/target + backend): the same key returns the same
  instance while it stays reachable — compiled expressions are stateless —
  and an entry cleared by the GC under memory pressure is recomputed on
  the next compile. `parseTree(source, varTypes)` exposes the raw
  tokenize→parse pipeline and `compileTree(root, backend)` compiles an
  existing tree; neither is cached. All compile paths pass the registry's
  scoped `typeEmissions` to the backend.
- `ext/math/` — `number` (typed literals, `NumberLiteralNode`), `operator`
  (same-type arithmetic, `BinaryArithmeticNode` + `NegationNode`),
  `bracket` (`( ) ,`), `function` (`ConvertNumericNode`, `MathFunctionNode`;
  conversions, typed `abs`/`min`/`max`, Double-only sqrt/pow/trig/etc. —
  `round` follows `kotlin.math.round`, ties to even), and the `MathSyntax`
  preset combining all of them plus `ext/variable/` (`VariableNode` —
  `$name` variables from the evaluation context).
- `ext/logic/` — `LogicExtension`: `true`/`false` literals, prefix `!`,
  short-circuit `&&`/`||`, same-type numeric comparisons `< <= > >=`,
  `==`/`!=` (numeric, String, Boolean), and lazy `if(cond, a, b)`.
- `ext/string/` — `StringExtension`: string literals with minimal escapes,
  `+` concat (a second `InfixOperatorParser` on the math `+` token, tried
  after arithmetic), `length`, `contains` (emitted as `indexOf >= 0`).
- `ext/calculus/` — `Differentiator`: `differentiate(root, by)` applies
  the nodes' `DifferentiableNode` rules (Double-only) and simplifies the
  result; `simplify` folds constants and collapses arithmetic identities,
  passing unknown node classes through unchanged.
- `ext/StandardSyntax.kt` — preset combining `MathSyntax` + `LogicExtension`
  + `StringExtension` in that order (so arithmetic `+` wins before concat).

## Conventions

- Tests mirror the main source tree (`src/test/kotlin/...` mirrors
  `src/main/kotlin/...`); add tests for every new token parser / extension.

# Development guide
Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.