# PLAN — Text-Based Calculator

All 16 requirements in SPEC.md are must-have — there are no nice-to-haves left, so the
milestones aren't split by priority. Instead: **Milestone 1** builds full happy-path
coverage of every must-have (REQ-001 through REQ-016, including `%` and unary `+`/`-`
— these are core grammar, not late add-ons). **Milestone 2** hardens the same
must-haves with their error paths and edge cases, since a requirement isn't done
until its failure mode is handled too, then cleans up. File-path CLI input is
explicitly out of scope (SPEC "Out of Scope") — stdin is the only required input source.

## Milestone 1: Minimal Working Solution (all must-haves, happy path)

1. **[CLEANUP]** Set up project skeleton: build tool (Maven or Gradle), packages
   `tokenizer`, `parser`, `ast`, `eval`, `cli`, and a test source set with JUnit 5.
   *Design ref*: Minimal Architecture.
   *DoD*: `test` task runs (zero tests yet); package layout matches the pipeline.

2. **[HAPPY PATH]** Implement `Token`/`TokenType` and `Tokenizer` for: `NUMBER` (digits,
   optional `.` and fraction digits), `IDENT`, `+ - * / % ( ) =`, `++`, `--`, with
   whitespace skipped. The tokenizer only captures raw text — it does not range-check.
   *Design ref*: Data Structures — Token.
   *DoD*: `TokenizerTest` tokenizes all 5 SPEC example lines correctly, plus a 30-digit
   literal (still just a raw token at this stage — range-checking happens in the
   parser, step 4), `3.14 * 2`, `7 % 3`, and `-5 + 3` into the expected token lists
   with raw text intact.

3. **[HAPPY PATH]** Implement `Value` (`IntValue(long)`, `FloatValue(double)`) and AST
   node classes: `NumberLiteral(Value)`, `VariableRef`, `BinaryOp`, `UnaryOp`,
   `PrefixIncDec`, `PostfixIncDec`, `AssignmentStatement`.
   *Design ref*: Data Structures — Value, AST.
   *DoD*: classes compile with fields + `equals`/`toString` usable in test assertions;
   a unit test constructs `IntValue` and `FloatValue` directly and checks basic
   equality/`toString`.

4. **[HAPPY PATH]** Implement the recursive-descent `Parser` for the full grammar —
   `statement/expression/term/unary/postfix/primary` (REQ-001, REQ-003, REQ-015,
   REQ-016): `+ - * / %` with correct precedence, parenthesized sub-expressions, and
   unary `+`/`-` (distinguished from the binary operators by grammar position). When
   building each `NumberLiteral`, classify the raw token text into `IntValue` (no `.`,
   parsed via `Long.parseLong`) or `FloatValue` (has `.`) — REQ-009, REQ-010. A digit
   string that overflows `long` throws `ParseException` ("integer literal out of range").
   *Design ref*: Main Flow step 2b; Data Structures — Value.
   *DoD*: `ParserTest` builds the correct AST for each SPEC example line; for
   `(5 + 3) * 10` confirms parens override default precedence; for `7 % 3` confirms a
   `%` `BinaryOp`; for `-5 + 3` and `3 - -5` confirms unary minus is distinguished from
   binary minus; for `9223372036854775807` (`Long.MAX_VALUE`) confirms a valid
   `IntValue` literal; for `99999999999999999999` (30 digits) confirms
   `ParseException`; for `3.14 * 2` confirms a `FloatValue` literal.

5. **[HAPPY PATH]** Extend the parser to prefix/postfix `++`/`--` bound to identifiers
   only (REQ-005).
   *Design ref*: Main Flow grammar, `postfix`/`primary` rules.
   *DoD*: `ParserTest` covers `++i`, `i++`, `--i`, `i--` producing the correct AST
   nodes. (Rejecting `(x+1)++` is deferred to Milestone 2 — must not crash the parser.)

6. **[HAPPY PATH]** Implement `Environment` (`LinkedHashMap<String, Value>`) and
   `Evaluator.execute(AssignmentStatement, Environment)` covering: plain `=` (adopts
   RHS kind), compound `+= -= *= /=` with Java-style narrowing back to the variable's
   *current* kind (REQ-004), `++`/`--` side effects evaluated eagerly in place using a
   same-kind `1`/`1L` (REQ-005), unary `+`/`-` (REQ-016), and the numeric
   promotion/dispatch rule for `BinaryOp` including `%` (REQ-011, REQ-015) using
   **native `long`/`double` operators** (not hand-rolled overflow logic).
   *Design ref*: Main Flow step 2c; Numeric Promotion & Arithmetic Dispatch.
   *DoD*: `EvaluatorTest` covers: (a) the 5 SPEC lines produce the documented
   intermediate state after each line; (b) `Long.MAX_VALUE + 1` wraps to
   `Long.MIN_VALUE` (REQ-008), not an error; (c) `5 + 1.5` promotes to
   `FloatValue(6.5)`; (d) `++`/`--` on a `FloatValue` variable changes it by `1.0`;
   (e) starting from `IntValue i=5`, `i += 2.5` narrows back to `IntValue(7)`,
   matching real Java's `long i = 5; i += 2.5;` (REQ-004); (f) `7 % 3 == 1` and
   `-7 % 3 == -1` (Java's sign-of-dividend remainder) and `7.5 % 2 == 1.5`; (g)
   `-5 + 3 == -2` and `-(2 + 3) == -5`.

7. **[HAPPY PATH]** Implement `Formatter`: `IntValue` → plain digits; `FloatValue` →
   calculator-style display (no `.0` when whole, e.g. `10.0 → "10"`; otherwise
   `Double.toString`, e.g. `6.28`; `NaN`/`Infinity`/`-Infinity` as literal words) —
   per the algorithm in DESIGN Main Flow step 3.
   *Design ref*: Main Flow step 3.
   *DoD*: `FormatterTest` covers `IntValue(10)` → `"10"`, `FloatValue(10.0)` → `"10"`,
   `FloatValue(6.28)` → `"6.28"`, `FloatValue(1.0/0.0)` → `"Infinity"`.

8. **[HAPPY PATH]** Implement the CLI entry point: read lines from stdin in order
   (REQ-006), run tokenizer→parser→evaluator per line against one shared `Environment`,
   then join the `Formatter`'s per-variable strings as `(k1=v1,k2=v2,...)` in
   insertion order and print to stdout (REQ-007).
   *Design ref*: Main Flow step 3; Interface Requirements.
   *DoD*: piping the SPEC example into the CLI prints exactly `(i=82,j=1,x=6,y=80)`;
   piping `f = 3.14 * 2` prints `(f=6.28)`; piping
   `big = 9223372036854775807 + 1` prints `(big=-9223372036854775808)`
   (overflow wraparound, REQ-008); piping `whole = 8.0 + 2.0` prints `(whole=10)`
   (not `10.0`); piping `x = 7 % 3` prints `(x=1)`; piping `x = -5 + 3` prints `(x=-2)`.

9. **[TEST]** Add `CalculatorIntegrationTest` feeding the exact SPEC example, an
   overflow-wraparound example, a float example, a whole-number-float example
   (`8.0 + 2.0`), a compound-assignment-narrowing example (`i=5; i+=2.5`), a modulo
   example, and a unary-minus example through the library entry point (not the CLI
   process), asserting exact output strings.
   *Design ref*: Testing Approach.
   *DoD*: all seven integration tests pass; these are the primary demo/regression
   tests for the interview.

**Milestone 1 exit criteria**: every must-have requirement's happy path — arithmetic
(including `%` and unary `+`/`-`), overflow, float, compound-assignment narrowing, and
stdin input — runs correctly end-to-end, with tests covering tokenizer, parser, evaluator,
formatter, and integration.

## Milestone 2: Hardened / Full Interview Solution (error paths & edge cases)

10. **[EDGE CASE]** Add `EvalException` for reading an undefined variable (REQ-012).
    *Design ref*: Error Handling Approach.
    *DoD*: `x = y + 1` (y never assigned) throws with the correct line number; CLI
    prints `line <n>: ...` to stderr and exits non-zero.

11. **[EDGE CASE]** Add `EvalException` for **integer** division/modulo by zero
    (REQ-014), and prove **float** division/modulo by zero does *not* throw.
    *Design ref*: Error Handling Approach; Numeric Promotion & Arithmetic Dispatch.
    *DoD*: `x = 5 / 0` and `x = 5 % 0` throw with a clear message; `x = 5.0 / 0`
    evaluates to `FloatValue(Infinity)` without throwing, and `x = 0.0 / 0.0`
    evaluates to `NaN` — both asserted directly in `EvaluatorTest`.

12. **[EDGE CASE]** Add `ParseException` for malformed input: unmatched parens,
    missing operand, invalid assignment target, `++`/`--` applied to a non-identifier
    (REQ-013).
    *Design ref*: Validation Approach.
    *DoD*: `ParserTest` has one case per malformed input above, each throwing
    `ParseException` with the correct line number.

13. **[EDGE CASE]** Skip blank/whitespace-only lines; empty overall input produces `()`.
    *Design ref*: SPEC Edge Cases.
    *DoD*: tests for blank-lines-only input and fully empty input assert correct output.

14. **[EDGE CASE]** Confirm re-assignment ordering and plain-`=` kind-switching: a
    variable assigned twice keeps its first-seen position but its last value, and its
    kind (`IntValue`/`FloatValue`) can change across a plain `=` (disclosed deviation
    from Java — SPEC "Deliberate Deviations").
    *Design ref*: SPEC Edge Cases; Environment (`LinkedHashMap<String, Value>`).
    *DoD*: test with `i=0` then later `i=5` then a newer variable `j=1` asserts output
    order is `(i=...,j=...)`; a separate test with `x=1` then `x=1.5` asserts the
    final printed value is `1.5`, not `1`.

15. **[TEST]** Add a parameterized precedence/associativity matrix: `2+3*4`,
    `(2+3)*4`, `10-3-2`, `2*3+4*5`, `2+3%4`, nested parens, unary minus combined with
    binary operators (`-2+3*-4`), plus mixed int/float variants of each (e.g.
    `2+3.0*4`).
    *Design ref*: Testing Approach.
    *DoD*: all cases pass against hand-computed expected values documented in the test.

16. **[CLEANUP]** Review all exception messages for clarity/consistency; add a
    README section with usage + the output format (including how whole-number
    floats display without `.0`); remove dead code and TODOs; run the full suite.
    *DoD*: full test suite green; README lets a stranger run the CLI and understand
    the output format without reading source.

**Milestone 2 exit criteria**: all 16 must-have REQs covered by passing tests,
including their error paths, with clear line-numbered error messages.

## Assumptions to Confirm with Interviewer

- Integers are Java `long` (64-bit signed), including real overflow wraparound —
  confirmed with you: no arbitrary-precision integers, literals outside `long`'s
  range are a parse error just like `javac`'s "integer number too large."
- Floating point is Java `double` (IEEE-754) — so float division by zero yields
  `Infinity`/`NaN` rather than an error.
- Output display is calculator-style — confirmed with you: a whole-number float
  prints without `.0` (e.g. `8.0 + 2.0` → `10`), which is the one deliberate,
  disclosed deviation from raw Java output; everything else (arithmetic, promotion,
  overflow, compound-assignment narrowing) targets exact Java fidelity.
- Compound assignment (`+=` etc.) applies Java's implicit narrowing-back-to-current-
  kind conversion; plain `=` does not (and may freely change a variable's kind,
  since this language has no static type declarations, unlike Java) — this
  asymmetry is intentional, matching real Java's own asymmetry between `=` and `+=`.
- `%` and unary `+`/`-` are must-have — confirmed with you — and are built as core
  grammar in Milestone 1, not deferred extras. File-path CLI input is out of scope
  (also confirmed with you) — stdin is the only required input source.
- One statement per line, no `;`-separated multi-statement lines.
- Fail-fast on the first error is acceptable (vs. reporting all errors found).
- Output ordering is "first appearance in input," matching the SPEC example.
- No requirement for multi-threaded/concurrent evaluation of the input script.
