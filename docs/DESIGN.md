# DESIGN — Text-Based Calculator

## Minimal Architecture

A classic small-interpreter pipeline, one statement (line) at a time:

```
stdin lines -> Tokenizer -> Parser -> Evaluator -> Environment -> Formatter -> stdout
                (tokens)     (AST)    (mutates)    (var state)
```

- **Tokenizer**: line of text → `List<Token>`. (REQ-001, REQ-003, REQ-004, REQ-005, REQ-013)
- **Parser**: `List<Token>` → one `AssignmentStatement` AST node, via hand-written
  recursive descent; also decides Integer-vs-Float kind for each numeric literal and
  rejects literals outside `long` range. (REQ-001, REQ-003, REQ-004, REQ-005, REQ-009,
  REQ-010, REQ-013)
- **Evaluator**: walks the AST against the shared `Environment`, applying side effects
  (`++`/`--`) and the assignment (including compound-assignment narrowing), dispatching
  arithmetic per the promotion rule using **native Java `long`/`double` operators**.
  (REQ-002, REQ-004, REQ-005, REQ-006, REQ-008, REQ-011, REQ-014)
- **Environment**: the variable state, shared and mutated across all lines. (REQ-006, REQ-007)
- **Formatter**: `Environment` → `(k=v,...)` string, kind-aware, with calculator-style
  display of whole-number floats. (REQ-007)

No parser-generator, no AST-visitor framework — plain classes and `switch`/`if` — to
keep every step explainable without introducing unfamiliar machinery.

## Data Structures

- `Token { TokenType type, String text, int column }`
  `TokenType`: `NUMBER, IDENT, PLUS, MINUS, STAR, SLASH, PERCENT, ASSIGN, PLUS_ASSIGN,
  MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN, INCREMENT, DECREMENT,
  LPAREN, RPAREN, EOF`. The tokenizer captures a `NUMBER` token's raw text as-is (digits,
  optionally followed by `.` and more digits — plain decimals only, no scientific
  notation); it does not itself interpret magnitude or range-check.

- **`Value`** — a small closed hierarchy representing the two supported numeric kinds,
  both backed by Java's own primitive types so their arithmetic *is* Java's arithmetic:
  - `IntValue(long raw)` — REQ-008, REQ-009
  - `FloatValue(double raw)` — REQ-010
  Built once, at the point a `NumberLiteral` is constructed in the parser: if the raw
  token text contains `.`, parse as `double` → `FloatValue`; otherwise attempt
  `Long.parseLong(text)` → `IntValue`. If that throws `NumberFormatException` (the
  digit string doesn't fit in a `long`), the parser raises `ParseException` — "integer
  literal out of range" (REQ-009), mirroring `javac`'s own "integer number too large"
  compile error for the same input.

- AST (`Expr` hierarchy):
  - `NumberLiteral(Value value)` — REQ-001, REQ-009, REQ-010
  - `VariableRef(String name)` — REQ-002
  - `BinaryOp(Expr left, Op op, Expr right)` — REQ-003, REQ-011
  - `PrefixIncDec(Op op, String varName)`, `PostfixIncDec(Op op, String varName)` — REQ-005
  - `UnaryOp(Op op, Expr expr)` — REQ-016

  `Statement`: `AssignmentStatement(String varName, AssignOp op, Expr rhs, int line)`.

- `Environment`: `LinkedHashMap<String, Value>` — insertion order gives REQ-007's
  "first appearance" ordering for free; O(1) average read/write. A variable's kind can
  change between `IntValue` and `FloatValue` across a plain `=` reassignment (SPEC
  "Deliberate Deviations"); compound assignment instead narrows back to the variable's
  current kind (REQ-004).

## Numeric Promotion & Arithmetic Dispatch (REQ-011)

One rule, applied at every `BinaryOp` evaluation:

> If either operand is a `FloatValue`, convert both to `double` and compute with
> Java `double` arithmetic. Otherwise both are `IntValue`; compute with Java `long`
> arithmetic.

This mirrors Java's own binary numeric promotion (`long`/`double` → `double`), just
collapsed to our two kinds instead of Java's five primitive numeric types. Crucially,
both paths use **Java's own primitive operators directly** (`a + b` on two `long`s, or
two `double`s) rather than a hand-rolled arithmetic routine — so overflow wraparound,
truncating integer division, and IEEE-754 float behavior all come from the JVM itself,
for free, with no special-case code required to reproduce them.

| Op | Int path (`long`) | Float path (`double`) |
|----|--------------------|------------------------|
| `+ - *` | native `long` ops — silently wraps on overflow (REQ-008), exactly like Java | native `double` ops |
| `/` | native `long` division — truncates like Java `long/long`; **zero divisor → `ArithmeticException`**, wrapped as `EvalException` (REQ-014) | native `double` division — zero divisor → `Infinity`/`-Infinity`/`NaN`, **not an error** (REQ-014) |
| `%` (REQ-015) | native `long` `%` — Java's sign-of-dividend remainder; zero divisor → `ArithmeticException` → `EvalException` | native `double` `%` — zero divisor → `NaN`, not an error |

`++`/`--` (REQ-005) add/subtract `1L` if the variable currently holds an `IntValue`
(wrapping exactly like Java `long x; x++;` at `Long.MAX_VALUE`), or `1.0` if it holds
a `FloatValue` — matching Java's `x++` behavior on whichever type `x` has.

**Compound assignment narrowing (REQ-004)**: `x op= rhs` is evaluated as Java itself
defines it — `x = (kind-of-x)(x op rhs)` — *not* as plain `x = x op rhs`. Concretely:
compute `x op rhs` using the promotion rule above (so an `IntValue x` combined with a
`FloatValue rhs` computes in `double`), then, if `x`'s *current* kind is `IntValue`,
narrow the double result back with a Java-style cast (`(long) doubleResult`, which
truncates toward zero) before storing. This reproduces the well-known Java quirk where
`long i = 5; i += 2.5;` compiles and leaves `i == 7`, even though `i = i + 2.5;` would
be a compile error for the same variable. Plain `=` skips this narrowing entirely and
just adopts the RHS's kind (SPEC "Deliberate Deviations").

## Main Flow

1. Read all input lines, tracking a 1-based line number for error messages.
2. For each non-blank line (blank lines skipped — see SPEC edge cases):
   a. `Tokenizer.tokenize(line)` → tokens (throws `TokenizeException` on bad characters).
   b. `Parser.parseStatement(tokens)` → `AssignmentStatement` (throws `ParseException`
      on bad grammar, including an out-of-`long`-range literal — REQ-009, REQ-013).
      Grammar, in precedence order (low → high):
      ```
      statement  := IDENT assignOp expression
      expression := term (('+' | '-') term)*
      term       := unary (('*' | '/' | '%') unary)*
      unary      := ('-' | '+') unary | postfix        (REQ-016)
      postfix    := primary ('++' | '--')?
      primary    := NUMBER | IDENT | '++' IDENT | '--' IDENT | '(' expression ')'
      ```
      `NUMBER` literal text is classified/range-checked into `IntValue`/`FloatValue`
      here, per the Data Structures section above.
   c. `Evaluator.execute(statement, env)`:
      - Evaluate `rhs` recursively; evaluating a `Prefix/PostfixIncDec` node both
        reads *and* mutates `env` immediately (matches Java's left-to-right,
        eager side-effect semantics — REQ-005).
      - Reading a `VariableRef` not present in `env` throws `EvalException` (REQ-012).
      - Binary ops dispatch via the promotion rule above, using native `long`/`double`
        operators; integer divide/modulo by zero throws `EvalException` (REQ-014);
        float divide/modulo by zero does not.
      - Apply the statement's assignment operator: plain `=` stores the RHS value as-is
        (adopting its kind); compound ops narrow back to the variable's current kind
        first (REQ-004). Compound ops require the variable to already exist. Store,
        preserving first-seen insertion order if the key already existed.
3. After all lines: `Formatter.format(env)` joins entries as `(k1=v1,k2=v2,...)` and
   prints to stdout (REQ-007). Per value:
   - `IntValue` → `Long.toString()` (plain digits).
   - `FloatValue` → **calculator-style formatting**:
     1. `NaN` → `"NaN"`; `±Infinity` → `"Infinity"`/`"-Infinity"` (Java's own strings).
     2. Else if the value has no fractional part (`raw == Math.rint(raw)`, and
        finite): format as a plain integer —
        `BigDecimal.valueOf(raw).toBigInteger().toString()`. Going through
        `BigDecimal.valueOf` (which parses `Double.toString`'s canonical shortest
        form rather than the exact binary value) avoids scientific notation and
        matches what a person reading `8.0 + 2.0` expects: `10`, not `10.0`.
     3. Else: `Double.toString(raw)` (Java's shortest round-trip decimal), e.g. `6.28`.
   - The value's *kind* (`FloatValue`) is unaffected by this — only display drops the
     `.0`; a later operation on it still follows float arithmetic rules (e.g. divide
     by zero still yields `Infinity`, not an `EvalException`).

## Validation Approach

Two stages, matching the three error classes in SPEC:

- **Syntactic** (tokenizing + parsing): structure of the line, numeric-literal kind
  classification, and `long`-range checking are done before any evaluation happens
  for that line. Catches REQ-009 and REQ-013 cases.
- **Semantic/runtime** (evaluation): checked while walking the AST against live state.
  Catches REQ-012 (undefined variable) and REQ-014 (integer divide/modulo by zero).

## Error Handling Approach

Exception hierarchy, each carrying the 1-based line number and a plain-English message:

```
CalculatorException (abstract)
 ├─ TokenizeException
 ├─ ParseException
 └─ EvalException
```

`main()` catches `CalculatorException` (and, separately, plain `RuntimeException` for
unexpected bugs), hands the failure to the logging component below, and exits
non-zero. Fail-fast (SPEC "Error Handling") — simplest to implement/test/explain;
traded off below against collecting all errors.

## Failure Logging

A small, separately-testable `logging` package is the *only* thing that renders a
caught failure for the user — there's no separate concise `println` in `Main`
itself, so stderr and the log file show the identical rendering:

- **`Logger`** — a one-method interface (`logFailure(FailureEvent)`), deliberately
  scoped to failures only, not a general info/debug/warn framework nobody asked for.
- **`FailureEvent`** — an immutable record capturing everything useful about one
  failure: timestamp, exception type, the 1-based line (`0` if not applicable),
  the offending input line's actual text (looked up from the original input, not
  just the exception's own message), the message, and the full stack trace. Built
  once via `FailureEvent.of(...)` so every `Logger` implementation renders the same
  data instead of each re-deriving it.
- **`ConsoleLogger`** / **`FileLogger`** — write the same `LogFormatter`-rendered
  text to a `PrintStream` (stderr by default — stdout stays reserved for the
  calculator's own output, per "Interface / API Requirements") and to a file
  (append mode, creating parent directories on first use) respectively.
- **`MultiLogger`** — fans one event out to several `Logger`s. `Main` wires up
  `new MultiLogger(new ConsoleLogger(), new FileLogger(path))`; adding an
  HTTP-backed `Logger` later is a new class plus one constructor argument here,
  with zero changes to `ConsoleLogger`/`FileLogger` themselves.

`Main` also catches plain `RuntimeException` (a bug, not a user-input error) in a
second `catch` block, purely so it gets the same `MultiLogger` treatment — logged
with a full stack trace to both stderr and the failure log file, same as a
`CalculatorException`, just without a line number to point at.

## Testing Approach

JUnit 5, one test class per layer plus one integration class:

- `TokenizerTest` — token streams for valid lines and one test per tokenization error;
  includes a decimal literal and a digit string that overflows `long`.
- `ParserTest` — AST shape for each grammar rule, precedence/associativity cases
  (parameterized), literal-kind classification (`IntValue` vs `FloatValue`), an
  out-of-`long`-range literal producing `ParseException`, one test per other parse error.
- `EvaluatorTest` — runs statements against a hand-built `Environment`, asserts
  resulting state; dedicated cases for: `Long.MAX_VALUE + 1` wrapping to
  `Long.MIN_VALUE`, int/float mixed-operand promotion, compound-assignment narrowing
  (`i=5; i+=2.5` → `i==7`), integer div/mod by zero erroring, float div/mod by zero
  **not** erroring (asserts `Infinity`/`NaN`), `++`/`--` on a float variable.
- `FormatterTest` — `IntValue` prints as plain digits; `FloatValue` prints without
  `.0` when whole (`10.0 → "10"`), with fraction otherwise (`6.28 → "6.28"`), and
  `NaN`/`Infinity`/`-Infinity` print as those literal words.
- `CalculatorIntegrationTest` — feeds the SPEC example end-to-end plus an overflow
  example, a float example, and a compound-assignment-narrowing example, asserts
  exact output strings; primary regression/demo test.
- `FailureEventTest` / `LogFormatterTest` / `ConsoleLoggerTest` / `FileLoggerTest` /
  `MultiLoggerTest` — the logging package tested the same way as everything else:
  each class in isolation (a fake in-memory `Logger` stands in for one of
  `MultiLogger`'s loggers; `ConsoleLogger`/`FileLogger` are checked against a captured
  `PrintStream`/a `@TempDir` file, not real stderr or a real log path).

## Productionization Notes

Out of scope for this exercise, but worth naming if asked:

- Swap `long`/`double` for `BigInteger`/`BigDecimal` if a future spec needs
  unbounded-precision arithmetic (explicitly out of scope here — see SPEC).
- Collect-all-errors mode (linter style) instead of fail-fast, for a better authoring UX.
- Package as a library (stable `Calculator.run(...)` API) with the CLI as a thin
  wrapper, so it can be embedded elsewhere.
- Streaming evaluation (don't buffer all lines) if input could be very large.
- An HTTP-backed `Logger` (ship failures to a monitoring endpoint) — the `logging`
  package (`Logger` interface + `MultiLogger`) is already shaped for this; it's a
  new class implementing `Logger`, not a redesign.

## Trade-offs

- **Hand-rolled recursive descent vs. ANTLR/JavaCC**: hand-rolled has zero
  dependencies and every line is explainable in an interview; a generator would be
  less code but opaque. Chosen per SPEC's "small, readable" non-functional requirement.
- **Materialized AST vs. parse-and-evaluate-in-one-pass**: a one-pass evaluator is
  less code, but a separate AST makes the parser and evaluator independently unit
  testable and mirrors the standard interpreter model — chosen for testability and
  clarity of explanation, at the cost of a handful of extra classes.
- **Native `long`/`double` instead of `BigInteger`/`BigDecimal`**: chosen specifically
  *because* Java fidelity was made the goal — Java's own `int`/`long` are fixed-width
  and wrap on overflow, and its operators aren't even defined for `BigInteger`/
  `BigDecimal` objects (no operator overloading in Java), so using them would make
  "operates the same way Java interprets it" impossible to claim. Using native `long`/
  `double` also means overflow wraparound and IEEE-754 float behavior come from the
  JVM itself for free — no hand-written overflow logic needed.
- **Two-kind promotion instead of Java's full numeric tower**: Java has
  `byte/short/int/long/float/double`; we collapse to just `long` and `double`,
  applying Java's own widening rule (`long` + `double` → `double`) between just those
  two. Keeps the evaluator to two arithmetic code paths instead of a full promotion
  matrix, while still being a genuine (if reduced) subset of Java's real promotion table.
- **Compound assignment narrows, plain assignment doesn't**: intentionally asymmetric,
  because that's what real Java does (JLS 5.2 "Compound Assignment Conversion" bakes
  an implicit narrowing cast into `+=`/`-=`/`*=`/`/=` that plain `=` doesn't get).
  Reproducing just this asymmetry — rather than "simplifying" it away in either
  direction — is what makes the fidelity claim meaningful instead of coincidental.
- **Fail-fast vs. collect-all-errors**: fail-fast is simpler to implement and test;
  a linter-style multi-error report is more user-friendly but adds error-recovery
  complexity not required by the SPEC.
- **`LinkedHashMap` for `Environment`**: gives REQ-007's ordering requirement without
  a second bookkeeping structure, at the (irrelevant here) cost of slightly more
  memory per entry than a plain `HashMap`.
- **Whole-number floats display without `.0`**: matches how a "regular calculator"
  shows results (requested explicitly), at the cost of the display no longer
  round-tripping the value's kind by itself — `10` in the output could have come
  from an `IntValue` or a whole-number `FloatValue`. Accepted because REQ-007 only
  requires printing final values, not their kind, and it's the one display-only,
  clearly-disclosed deviation from otherwise strict Java fidelity.
