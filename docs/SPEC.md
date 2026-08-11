# SPEC — Text-Based Calculator

## Overview

A program that reads a series of assignment statements (one per line), written in a
subset of Java numeric expression syntax, evaluates them in order against a shared
set of variables, and prints the final value of every variable that was assigned.

No use of a scripting engine (Rhino/Nashorn/`eval`-equivalents) — expressions are
parsed and evaluated by code we write.

**Design goal — Java fidelity**: wherever this language overlaps with real Java
(arithmetic, operator precedence, numeric promotion, overflow, division-by-zero
behavior), the calculator must produce the exact same result Java itself would —
using `long` for integer variables and `double` for floating-point variables, the
same way `javac`/the JVM would evaluate the equivalent code. Only the two deliberate,
disclosed deviations below exist — everything else should match Java.

## Deliberate Deviations from Real Java

1. **Output formatting** (REQ-007): a whole-number float prints without `.0`
   (`10`, not `10.0`) — calculator-style, not `Double.toString`. The underlying
   value and all further arithmetic on it still behave exactly as a Java `double`
   would (e.g. it still yields `Infinity` on divide-by-zero); only the *display*
   differs from raw Java output.
2. **No static variable typing** for plain `=`: this language has no type
   declarations (`i = 0`, not `long i = 0`), so a variable's kind is whatever its
   most recent plain `=` assignment produced, and a later plain `=` may freely
   change it (`x = 1` then `x = 1.5` is allowed, unlike a real Java `long x` which
   would reject an unchecked narrowing assignment at compile time). **Compound
   assignment (`+=` etc.) does not get this leeway** — it follows Java's actual
   compound-assignment-operator rule instead (see REQ-004).

## Functional Requirements

All requirements below are must-have. Each is testable in isolation.

| ID | Requirement | Must/Nice |
|----|-------------|-----------|
| REQ-001 | Parse and evaluate a simple assignment `var = <numeric literal>`. | Must |
| REQ-002 | Support variable references inside expressions (a variable previously assigned). | Must |
| REQ-003 | Support binary operators `+ - * /` with correct precedence, plus parentheses to override precedence. | Must |
| REQ-004 | Support compound assignment operators `+= -= *= /=`, applying Java's implicit compound-assignment narrowing: the result is cast back to the variable's *current* kind before storing, exactly like Java's `x += y` (unlike Java's plain `x = y`, which would reject the same narrowing at compile time). E.g. if `i` currently holds a long, `i += 2.5` computes `i + 2.5` as a double, then truncates back to long — matching `long i = 5; i += 2.5;` in real Java, which compiles and leaves `i == 7`. | Must |
| REQ-005 | Support prefix and postfix increment/decrement (`++x`, `x++`, `--x`, `x--`) on variables only, with Java-matching value/side-effect semantics. Applying `++`/`--` to anything other than a variable is a reported syntax error, matching Java's "variable expected" compile error. | Must |
| REQ-006 | Process a multi-line series of statements in order, mutating one shared variable state across lines. | Must |
| REQ-007 | After processing all input, print every assigned variable and its final value as `(k1=v1,k2=v2,...)`, ordered by each variable's first appearance in the input. A float value with no fractional part prints without a decimal point (`10`, not `10.0`) — see "Deliberate Deviations" above. | Must |
| REQ-008 | Integer values behave exactly like Java `long` (64-bit signed, two's-complement): arithmetic that overflows the range wraps around silently (e.g. `Long.MAX_VALUE + 1 == Long.MIN_VALUE`) — this is **not** an error, matching real Java `long` overflow. | Must |
| REQ-009 | An integer **literal** whose value doesn't fit in `long`'s range (`[-9223372036854775808, 9223372036854775807]`) is a reported syntax error at parse time — matches `javac`'s "integer number too large" compile error. (This is distinct from REQ-008: a literal out of range never runs at all in Java; a *computed* result out of range silently wraps.) | Must |
| REQ-010 | Floating-point literals (e.g. `3.14`) and arithmetic on them are supported, using Java `double` (IEEE-754) semantics. | Must |
| REQ-011 | Numeric promotion: a binary operation where at least one operand is floating-point produces a floating-point result (matches Java's `long`/`double` binary numeric promotion); an operation between two integers stays integer, including Java's truncating `long`/`long` division. | Must |
| REQ-012 | Reading a variable that was never assigned is a reported error, not a silent default (e.g. `0`). | Must |
| REQ-013 | Malformed syntax (unmatched parens, missing operand, invalid assignment target) is a reported error identifying the offending line. | Must |
| REQ-014 | Integer division or modulo by zero is a reported error (matches Java's `ArithmeticException` for `long`). Floating-point division by zero follows IEEE-754 (`Infinity`/`NaN`) and is **not** an error — matches real Java `double` behavior. | Must |
| REQ-015 | Support the modulo operator `%`. | Must |
| REQ-016 | Support unary `+`/`-` in expressions, e.g. `x = -5 + 3`. | Must |

## Happy Path

```
i = 0
j = ++i
x = i++ + 5
y = (5 + 3) * 10
i += y
```
→ `(i=82,j=1,x=6,y=80)`

Additional happy-path examples:

```
big = 9223372036854775807 + 1
```
→ `(big=-9223372036854775808)` — `Long.MAX_VALUE + 1` wraps to `Long.MIN_VALUE`,
exactly like real Java `long` overflow (REQ-008). Not an error.

```
f = 3.14 * 2
mix = 5 + 1.5
whole = 8.0 + 2.0
```
→ `(f=6.28,mix=6.5,whole=10)` — `mix` is float because one operand (`1.5`) is float
(REQ-011); `whole` is computed as a float internally (real Java would print `10.0`),
but displays as `10` per the calculator-style formatting deviation (REQ-007).

```
i = 5
i += 2.5
```
→ `(i=7)` — compound assignment narrows the double result back to `i`'s long kind,
truncating `7.5` to `7`, exactly like `long i = 5; i += 2.5;` in real Java (REQ-004).

## Edge Cases

- Empty input → output `()`.
- Blank / whitespace-only lines are ignored.
- A variable assigned more than once keeps its *first-seen* position in the output
  but its *last* value.
- Nested side effects in one expression, e.g. `x = i++ + i++`.
- Variable names are case-sensitive (`i` and `I` are distinct).
- Extra/irregular whitespace around tokens is accepted (`i=0`, `i = 0`, `i  =  0`).
- Integer arithmetic that overflows `long`'s range wraps around (two's-complement),
  it does not error and does not grow beyond 64 bits — REQ-008.
- An integer literal too large to fit in `long` (e.g. `99999999999999999999`) is a
  parse-time error, never evaluated — REQ-009.
- `++`/`--` on a float-valued variable increments/decrements by `1.0`, not `1`.
- Integer-only expressions never "leak" into floating point (e.g. `5 / 2` stays
  integer division `2`, it does not become `2.5`) — REQ-011.
- Floating-point division by zero produces `Infinity`/`-Infinity`/`NaN`, printed as
  such, rather than raising an error — REQ-014.
- A float result that happens to be a whole number (e.g. `8.0 + 2.0`) prints as `10`,
  not `10.0` — the value's *kind* stays float internally (so a later `/0` on it still
  yields `Infinity`, not a divide-by-zero error), only the *display* drops the `.0`.
- `i += 2.5` on a currently-integer `i` truncates back to integer after computing in
  double (REQ-004) — different from `i = i + 2.5`, which (per the disclosed
  deviation) is allowed here and turns `i` into a float, where real Java would
  refuse to compile the equivalent plain assignment at all.

## Error Handling

- **Fail-fast**: processing stops at the first error; nothing after it is evaluated.
- Errors are reported with the 1-based input line number and a human-readable message
  (not a raw exception stack trace) — see REQ-009, REQ-012, REQ-013, REQ-014.
- Distinguish three error classes for clarity when explaining the design:
  lexical (bad token), syntactic (bad grammar, including out-of-range literals and
  `++`/`--` on a non-variable), semantic/runtime (undefined variable, integer
  divide/modulo by zero).

## Data Requirements

- Variable names: Java-identifier-like, `[a-zA-Z_][a-zA-Z0-9_]*`.
- Values: one of two kinds —
  - **Integer**: Java `long` (64-bit signed, two's-complement overflow).
  - **Float**: `double` (IEEE-754), Java's own floating-point type.
  A variable's kind is whatever its most recent assignment produced. A plain `=`
  may freely change a variable's kind (disclosed deviation, above); `+=`/`-=`/`*=`/
  `/=` narrow back to the variable's current kind instead (REQ-004, matching Java).
- Input encoding: UTF-8 text, one statement per line.
- Output: single line, exact format `(k1=v1,k2=v2,...)`. Integers print as plain
  digits (`82`). Floats print without a decimal point when they have no fractional
  part (`10`, not `10.0` — calculator-style display), otherwise using Java's
  shortest round-trip decimal representation (`6.28`); `Infinity`/`-Infinity`/`NaN`
  print as such. See DESIGN for the exact formatting algorithm.

## Interface / API Requirements

- Primary interface: CLI reads statements from **stdin** until EOF, then prints the
  result line to **stdout**. Errors go to **stderr** with non-zero exit code.
- The evaluation logic is exposed as a plain library call independent of stdin/stdout
  (e.g. `Calculator.run(List<String> lines) -> String`) so it can be unit/integration
  tested without process I/O.

## Non-Functional Requirements

- Code must be small enough to explain line-by-line in an interview.
- No external parsing-framework dependency (hand-rolled lexer/parser) — keeps the
  solution self-contained and fully explainable.
- Deterministic: same input always produces the same output.
- Single-threaded batch evaluation — input is an ordered script, so there is no
  concurrent-access requirement (see Out of Scope).

## Out of Scope

- String/boolean types, comparisons, logical operators, control flow (`if`/`while`/`for`), functions.
- Arbitrary-precision integers (`BigInteger`) or decimals (`BigDecimal`) — integers
  are Java `long` exactly, including overflow wraparound; floats are `double`.
- Scientific-notation literals (`1e10`) — only plain decimal literals (`3.14`) are required.
- Full Java static type-checking for plain `=` assignment (see "Deliberate Deviations").
- File-path CLI input — the CLI only needs to read from stdin; taking a file path as
  an argument is not required (the library entry point, `Calculator.run(List<String>)`,
  is testable without process I/O regardless).
- Multi-threaded or concurrent evaluation.
- Persistence of variable state across separate program runs.
- Full Java grammar compliance (e.g. bitwise ops, explicit casts, `int`/`float` as
  distinct types from `long`/`double`).
- GUI / network interface.
