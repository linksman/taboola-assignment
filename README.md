# taboola-assignment

A text-based calculator: reads a series of assignment statements (one per line,
Java-like syntax) and prints the final value of every variable that was assigned.

See `docs/SPEC.md`, `docs/DESIGN.md`, `docs/PLAN.md`, and `docs/DISCUSSION.md` for
the full requirements, architecture, build plan, and design-decision writeup
behind this implementation.

## Build & test

```bash
mvn test                                   # run the full test suite
mvn test -Dtest=EvaluatorTest              # run one test class
mvn test -Dtest=EvaluatorTest#methodName   # run one test method
mvn package                                # also produce target/calculator-1.0-SNAPSHOT.jar
mvn compile                                # compile only, no packaging
```

## Run

There are two ways to run the CLI, depending on whether you want the packaged
artifact or the fastest inner dev loop. Both read statements from **stdin** until
EOF (Ctrl+D if typing interactively) and print one result line to **stdout**.

### As a packaged jar

```bash
mvn package
java -jar target/calculator-1.0-SNAPSHOT.jar
```

```bash
# piped input
printf 'i = 0\nj = ++i\nx = i++ + 5\ny = (5 + 3) * 10\ni += y\n' \
  | java -jar target/calculator-1.0-SNAPSHOT.jar
# (i=82,j=1,x=6,y=80)

# input redirected from a file
java -jar target/calculator-1.0-SNAPSHOT.jar < input.txt
```

### Directly via the classpath (no packaging step)

Useful while iterating — skips `mvn package` entirely and just runs the compiled
`cli.Main` class straight off `target/classes`:

```bash
mvn compile
java -cp target/classes com.guylinksman.calculator.cli.Main
```

```bash
printf 'x = 7 %% 3\n' | java -cp target/classes com.guylinksman.calculator.cli.Main
# (x=1)
```

Both forms are the same `Main` class — the jar just bundles it with a manifest
(`Main-Class`) so you don't need to name the class or set `-cp` by hand.

## Input syntax

One assignment per line: `var = expression` or `var <op>= expression` where
`<op>` is `+ - * /`. Expressions support `+ - * / %`, parentheses, unary `+`/`-`,
and prefix/postfix `++`/`--` on variables — the same precedence and side-effect
rules as the equivalent Java code.

## Arithmetic semantics

**Numeric promotion** — the core evaluation rule, applied at every binary
operation: if either operand is a float, convert both to `double` and compute
with Java `double` arithmetic; otherwise both are integers and compute with Java
`long` arithmetic — using Java's own primitive operators directly, so overflow
wraparound, truncating integer division, and IEEE-754 float behavior all come
for free from the JVM (no hand-rolled arithmetic). Integer divide/modulo by zero
is a reported error; float divide/modulo by zero is not (yields
`Infinity`/`NaN`, matching Java `double`).

**Compound assignment narrows, plain assignment doesn't** — intentionally
asymmetric, because that's what real Java does. `x op= rhs` computes `x op rhs`
under the promotion rule above, then, if `x` is currently an integer, truncates
the result back to `long` before storing (`i=5; i+=2.5` → `i==7`). Plain `=`
skips narrowing entirely and just adopts the RHS's kind.

## Deliberate deviations from Java fidelity

The design goal is Java fidelity: wherever this language overlaps with real
Java, results must match exactly what `javac`/the JVM would produce. Only two
deliberate, disclosed deviations exist:

1. A whole-number float displays without `.0` (`10`, not `10.0`) —
   calculator-style. Display only; the value's kind and all further arithmetic
   on it still follow `double` rules (e.g. divide-by-zero still yields
   `Infinity`, not an error).
2. Plain `=` has no static typing and may freely change a variable's kind
   (`x = 1` then `x = 1.5`), unlike real Java which would reject that narrowing
   at compile time. Compound assignment does *not* get this leeway (see above).

See `docs/SPEC.md` ("Deliberate Deviations") for the full rationale.

## Error handling

Fail-fast: processing stops at the first error, nothing after it is evaluated.
On error, the process exits with a non-zero status and stdout stays empty. The
full failure detail — timestamp, exception type, the line-numbered message,
the offending source line, and a stack trace — is written to **stderr** and to
`logs/calculator-failures.log` alike (same `MultiLogger` fan-out, same
rendering) — see `docs/DESIGN.md` § Failure Logging.

## Concurrency

Single-threaded *within one run* is a semantic requirement (`x = i++ + i++`
only has one defined answer because evaluation order is strictly
left-to-right; later lines can depend on earlier ones), not a shortcut. Where
concurrency is actually relevant: `Calculator.run(...)` is safe to call
concurrently from multiple threads for *independent* inputs — every call
constructs its own `Tokenizer`/`Evaluator`/`Environment`, so there's no shared
mutable state across calls. `Environment`'s backing `LinkedHashMap` is not
synchronized, and is only safe because each `run()` call owns a private one;
sharing a single `Environment` across concurrent writers is explicitly out of
scope. See `docs/DISCUSSION.md` § Concurrency for the full audit.

## Output format

`(var1=val1,var2=val2,...)`, variables in the order they first appeared in the
input. Two numeric kinds are tracked internally, both backed by real Java
primitives so arithmetic (including overflow and division-by-zero behavior)
matches what the same code would do in Java:

- **Integers** — Java `long` (64-bit signed), including overflow wraparound.
- **Floats** — Java `double` (IEEE-754), displayed without a trailing `.0` when
  whole (see "Deliberate deviations" above).

## Project structure

One-directional pipeline, one statement (line) at a time:

```
stdin lines -> Tokenizer -> Parser -> Evaluator -> Environment -> Formatter -> stdout
                (tokens)     (AST)    (mutates)    (var state)
```

No parser-generator, no AST-visitor framework — plain classes and `switch`/`if`,
chosen so every step stays explainable line-by-line.

```
taboola-assignment/
├── pom.xml                    Maven build — Java 17, JUnit 5, no other runtime deps
├── README.md                  this file
├── docs/
│   ├── SPEC.md                 what the calculator must do — numbered, testable requirements
│   ├── DESIGN.md               how it's built — architecture, data structures, trade-offs
│   ├── PLAN.md                 the milestone-by-milestone build plan
│   └── DISCUSSION.md           simplicity / OOP / concurrency / optimization write-up
└── src/
    ├── main/java/com/guylinksman/calculator/
    │   ├── Calculator.java      library entry point — run(List<String>) -> String
    │   ├── tokenizer/           text -> tokens (Tokenizer, Token, TokenType)
    │   ├── parser/               tokens -> AST (Parser — hand-written recursive descent).
    │   │                         Also classifies each numeric literal as int/float and
    │   │                         range-checks it against `long` here — an out-of-range
    │   │                         literal is a parse-time error, not a runtime one.
    │   ├── ast/                  AST node types — the shared vocabulary between `parser`
    │   │                         and `eval`: Expr (sealed: NumberLiteral, VariableRef,
    │   │                         BinaryOp, UnaryOp, Prefix/PostfixIncDec), AssignmentStatement,
    │   │                         operator enums, and Value (IntValue(long)/FloatValue(double))
    │   ├── eval/                  AST -> mutated state (Evaluator, Environment, Formatter).
    │   │                         Environment is a LinkedHashMap, giving "first appearance"
    │   │                         output ordering for free.
    │   ├── error/                 exception hierarchy — CalculatorException (abstract,
    │   │                         carries the 1-based line number) -> TokenizeException /
    │   │                         ParseException / EvalException
    │   ├── logging/                failure logging (Logger, FailureEvent, Console/File/MultiLogger —
    │   │                         Main fans every caught failure out to both stderr and the file)
    │   └── cli/                    process wrapper — stdin in, stdout/stderr out (Main).
    │                             The only package that touches stdin/stdout/the filesystem.
    └── test/java/com/guylinksman/calculator/
        ├── (mirrors main/ — one test class per production class, same package)
        └── integration/          end-to-end tests against Calculator.run(...) directly,
                                   including a concurrent-execution stress test
```

Data flows in one direction through the `main` packages, top to bottom of that
list: `tokenizer` → `parser` → `eval`, with `ast` as the shared vocabulary between
`parser` and `eval`, and `error`/`logging` cutting across all of them. `cli` is the
only package that touches stdin/stdout/the filesystem — everything else is a pure
function over its inputs, which is what makes the `eval` layer trivial to
unit-test and safe to call concurrently (see `docs/DISCUSSION.md` § Concurrency).

`Calculator.run(List<String>) -> String` is the library entry point,
independent of process I/O; `cli.Main` is a thin wrapper around it.

Tests mirror `main/` one-to-one (one test class per production class, same
package). Logging classes are tested in isolation using a fake in-memory
`Logger` and `@TempDir`/captured `PrintStream`, never real stderr or a real log
file path.
