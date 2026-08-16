# DISCUSSION — Simplicity, OOP, Concurrency, Optimizations

The assignment's own instructions frame the evaluation around "Software Engineering,
Optimizations, Concurrency Issues and Architecture." SPEC/DESIGN/PLAN cover *what*
was built and *why*, requirement by requirement; this doc steps back and addresses
those four evaluation axes directly, plus a few other coding decisions worth being
able to explain out loud. It's written to be read alongside the code, with concrete
file references rather than abstract claims.

## Simplicity

- **Zero production dependencies.** The only dependency in `pom.xml` is
  `junit-jupiter`, scoped to `test`. No parser-generator, no DI framework, no
  logging framework, no `Optional`-of-`Optional` layering. `Calculator.run(...)` is
  a `public static` library entry point; `cli.Main` is a dozen lines of stdin/stdout
  glue around it.
- **No interfaces without a second implementation.** `Environment`, `Evaluator`,
  `Tokenizer`, `Formatter` are all concrete `final` classes, not
  `interface` + `impl` pairs. There's exactly one implementation of each, no plugin
  point is required by the spec, and an interface with one implementer is pure
  ceremony — easy to add later (`extract interface` is a trivial refactor) if a real
  second implementation ever shows up.
- **No configuration.** No properties files, no environment-variable knobs, no
  feature flags. The one behavior that *could* have been configurable — fail-fast
  vs. collect-all-errors — is a fixed, documented choice (DESIGN "Trade-offs"),
  because the spec doesn't ask for the other mode and a config option nobody
  exercises is just an untested branch.
- **The grammar is flat by design.** `statement := IDENT assignOp expression` — one
  statement shape, no control flow, no function calls. `AssignmentStatement` is a
  single record, not a sealed hierarchy of statement kinds, precisely because there
  is only one kind. It's deliberately *not* named a bare `Statement` — that name
  would imply a generality (multiple statement kinds to come) that this grammar
  doesn't have and isn't meant to grow into.
- **Fail-fast error handling** (`CalculatorException` propagates immediately,
  `Main` catches it once at the top) avoids the added complexity of an error-recovery
  scheme (collecting multiple errors, resynchronizing the parser after a bad token,
  etc.) that nothing in the spec asks for.

## Object-Oriented Design

- **Sum types via `sealed interface` + `record`, not classic inheritance.**
  `Expr` (`NumberLiteral`, `VariableRef`, `BinaryOp`, `UnaryOp`, `PrefixIncDec`,
  `PostfixIncDec`) and `Value` (`IntValue`, `FloatValue`) are closed hierarchies with
  no shared mutable state or shared behavior to inherit — only *shape* varies. Java's
  `sealed` + `record` combination models that directly: the compiler enforces
  exhaustiveness (the `permits` set is fixed and known), and every node is
  immutable by construction. A classic abstract-class hierarchy with an `evaluate()`
  method on each node (the Visitor/"tell, don't ask" GoF style) was considered and
  rejected: it would have scattered evaluation logic across seven small classes
  instead of keeping it in one place (`Evaluator`) that's easy to read top-to-bottom
  and easy to unit test as a unit.
- **One class, one job** — a textbook pipeline: `Tokenizer` (text → tokens),
  `Parser` (tokens → AST), `Evaluator` (AST → `Value`, plus `Environment` mutation),
  `Formatter` (`Environment` → `String`). Each is independently unit-tested
  (`TokenizerTest`, `ParserTest`, `EvaluatorTest`, `FormatterTest`) without needing
  the others, which is the practical payoff of the separation, not just a diagram.
- **Immutability everywhere it's free.** `Token`, every `Expr` node, and `Value`
  are records — final fields, structural `equals`/`hashCode`/`toString` generated,
  safely shareable with no defensive copying. The only mutable object in the whole
  system is `Environment`, and its mutability is the *point* — it's the variable
  state the language is defined around.
- **Encapsulation, with one deliberate leak.** `Environment` hides its backing
  `LinkedHashMap` behind `has`/`get`/`set` — except `asMap()` (used only by
  `Formatter`), which returns the live, mutable map rather than a read-only view or
  an iterator. This was a conscious "not worth it yet" call, not an oversight: the
  only caller is `Formatter.format`, in the same codebase, which only reads it. If
  `Environment` ever gained more callers, wrapping the return in
  `Collections.unmodifiableMap` would be a one-line fix — flagging it here rather
  than pretending it isn't a compromise.
- **A small exception hierarchy, used as an OOP hierarchy should be.**
  `CalculatorException` (abstract) → `TokenizeException` / `ParseException` /
  `EvalException`. `Main` catches the base type once; tests catch the specific
  subtype they're asserting on. This is inheritance used for what it's actually
  good at — a family of things that are substitutable at one call site but
  distinguishable at another — rather than inheritance used to share code.

## Concurrency

The instructions call this out explicitly, so it's worth being precise about what
was and wasn't done, and why.

**The evaluator is intentionally single-threaded *within one run*, and that's a
semantic requirement, not a shortcut.** `x = i++ + i++` only means anything specific
because "evaluate the left operand, then the right operand" is a defined order:
`Evaluator.evaluate` recurses strictly left-to-right on a `BinaryOp`, evaluating
and applying `left`'s side effects before it even looks at `right`, so the two
`i++`s are guaranteed to happen in source order rather than in some
implementation-dependent or parallel order. Statements within one input are
likewise inherently sequential: line 5 can depend on what line 3 assigned. There is no
"embarrassingly parallel" version of evaluating one calculator script — parallelizing
it would either be a no-op (all the real work is on the critical path) or change the
answer. So SPEC's "no concurrent-access requirement" isn't dodging the question —
it's the correct answer for *this* grammar.

**Where concurrency genuinely is relevant: multiple independent runs at once**, e.g.
this library embedded in a server handling many requests in parallel. Auditing each
class for that scenario:

| Class | Thread-safety | Why |
|---|---|---|
| `Tokenizer` | Safe to share across threads | No instance fields; `tokenize(...)` only touches its own locals and its arguments. |
| `Parser` | Safe (indirectly) | The public surface is the static `parseStatement(...)`; the mutable `pos` field lives on a `Parser` instance created fresh inside that call and never escapes it. Nothing to share, so nothing to race. |
| `Evaluator` | Safe to share across threads | No instance fields; all mutation happens on the `Environment` passed in as an argument. |
| `Formatter` | Safe to share across threads | No instance fields; pure function of the `Environment` it's given. |
| `Value` (`IntValue`/`FloatValue`), `Token`, every `Expr` node | Safe to share (immutable) | Records — no setters, nothing to race on. |
| `Environment` | **Not** thread-safe on its own — safe *because of how it's used* | Backed by a plain (unsynchronized) `LinkedHashMap`. `Calculator.run(...)` creates exactly one `Environment` per call and never exposes it to the caller or to another thread. As long as nobody starts sharing an `Environment` across concurrent calls, this is fine by construction, not by locking. |

**The practical upshot**: `Calculator.run(List<String>)` is safe to call concurrently
from multiple threads *for independent inputs* — each call gets its own
`Tokenizer`/`Evaluator`/`Environment`, so there's no shared mutable state between
calls at all. That's the scenario that would actually matter if this were exposed as
a service (many users, each with their own script). What would need real
synchronization work — and is explicitly out of scope, not just unimplemented — is
something the spec never asked for: multiple threads mutating *one shared*
`Environment` concurrently (e.g. a hypothetical multi-writer global-variables
feature). That would need at minimum a `ConcurrentHashMap` and, more importantly, a
decision about what atomicity a *statement* (not just a single map write) should
have — `i += 1` is a read-modify-write, and "two threads run `i += 1`
concurrently" has no well-defined answer without a locking or CAS strategy the
language spec doesn't define. Better to say that plainly than to bolt on a
`synchronized` keyword that gives a false sense of correctness.

**Considered and deliberately not done: building a statement dependency graph
to run independent statements of one script concurrently.** In principle a
script is a small data-flow DAG — build a read/write set per statement, and
any two statements with no edge between them (neither reads what the other
writes, nor writes what the other writes) could run on separate threads. Three
reasons this isn't in the design:

1. **The read/write set isn't just the assignment target.** Side effects live
   inside expressions, not only at the statement's `varName`: `y = x++ + z--`
   both reads and writes `x` and `z` as a side effect of evaluating the RHS,
   and a compound assignment (`i += 1`) both reads and writes its own target.
   Computing a correct dependency graph means walking every `Expr` node of
   every statement for embedded `PrefixIncDec`/`PostfixIncDec` reads/writes,
   not just diffing top-level assignment targets — real analysis work, and
   easy to get subtly wrong in a way that silently changes results rather than
   failing loudly (the worst kind of bug for a system whose entire premise is
   exact Java fidelity).
2. **Fail-fast has to stay in program order even if execution isn't.** SPEC
   requires reporting the *first* error in the input, not the first one a
   scheduler happens to hit. If two independent-looking statements ran on
   separate threads and the later one failed first, correctly reporting "the
   earlier one's error, if any, wins" needs its own synchronization/ordering
   logic layered back on top of the parallel execution — clawing back a good
   chunk of whatever concurrency was gained, for a script size where that gain
   was never going to be measurable to begin with.
3. **There's no workload here for it to pay off on.** The target input is a
   short, human-typed script — a handful of statements, each a few AST nodes
   of `long`/`double` arithmetic (nanoseconds of work). Building a dependency
   graph, spinning up a thread pool or work-stealing executor, and
   synchronizing results costs more wall-clock time than just running the
   statements in order would ever take. This is the general rule that
   parallelizing a computation cheaper than the parallelization overhead
   itself is a net loss, not a case-by-case judgment call for this input size.

None of this is a claim that statement-level parallelism is impossible for a
language like this in general — a bytecode-compiled batch job processing
thousands of largely-independent statements could plausibly benefit from
exactly this kind of dependency-graph scheduling. It's that doing so here
would spend real complexity (and real correctness risk, per point 1) solving
a performance problem this system doesn't have.

## Optimizations

Performance wasn't the bottleneck for an exercise processing a handful of lines,
but the design doesn't leave obvious inefficiency on the table either:

- **Native `long`/`double` instead of `BigInteger`/`BigDecimal`.** Beyond the
  semantic reasons (DESIGN "Trade-offs"), this is a real performance choice too —
  primitive arithmetic is a single JVM instruction; `BigInteger.add(...)` allocates
  a new object and runs arbitrary-precision arithmetic every time, even for `1 + 1`.
- **`LinkedHashMap` for `Environment`** gives O(1) average `get`/`put` *and*
  insertion-order iteration in one structure, instead of a `HashMap` plus a
  separately-maintained `List<String>` of insertion order (which would also need to
  be kept in sync on every write and dedupe on re-assignment).
- **Single-pass, backtracking-free tokenizing and parsing.** `Tokenizer.tokenize`
  is a hand-written character scan, O(line length), no regex engine, no
  backtracking. `Parser` is a predictive recursive-descent parser — each token is
  consumed at most once (`advance()`/`expect(...)`), so parsing one statement is
  O(number of tokens in it), not O(n²) from re-scanning on failed alternatives.
- **`Formatter`'s whole-number check is cheap, and the expensive path is
  avoided when it's not needed.** `raw == Math.rint(raw)` is an O(1) float
  comparison; the `BigDecimal.valueOf(...).toBigInteger()` conversion — the
  relatively expensive part — only runs for the whole-number branch, not on every
  formatted value.
- **No AST accumulation across the run.** `Calculator.run`'s loop discards each
  line's `List<Token>` and `AssignmentStatement` as soon as that line is evaluated —
  they're loop-local and immediately GC-eligible. Memory use during a run is
  O(distinct variables) for `Environment` plus O(size of the *current* statement)
  for its AST, not O(total input size). The one place total input size *is* held in
  memory is `cli.Main` buffering all of stdin into a `List<String>` before starting
  — called out already in DESIGN's "Productionization Notes" as the thing to change
  (stream line-by-line) if arbitrarily large input were a real requirement.
- **Considered and deliberately not done: value interning.** Java's own `Integer`
  cache (small boxed `int`s reused instead of reallocated) has an analogue here —
  interning common `IntValue`s (e.g. 0, 1) to cut allocations. Not done, because
  `IntValue`/`FloatValue` are small immutable records that the JIT can trivially
  scalar-replace/escape-analyze in the hot paths that matter (`++`/`--`, binary
  ops), so hand-rolled interning would add code and a cache-invalidation-shaped
  surface for a saving the JIT already gives away for free.

## Other Notable Decisions

- **Idiomatic modern Java (17 target).** Records, sealed interfaces, `instanceof`
  pattern matching, and arrow-style `switch` are used throughout rather than
  writing this as if it were Java 8 — e.g. `Value` and every `Expr` variant are
  records instead of hand-written classes with manual `equals`/`hashCode`.
- **Errors carry a line number by construction, not by convention.**
  `CalculatorException`'s constructor requires a `line` argument — there's no code
  path that can throw a calculator error without one, so "which line failed" can't
  be accidentally dropped somewhere in the call chain.
- **Iterative naming/structure refinement during development** — `Lexer` →
  `Tokenizer` (and `LexException` → `TokenizeException` to match), the package
  moving from `com.taboola.calculator` to `com.guylinksman.calculator`. Mentioned
  here only because it's a real signal, not noise: each rename was applied
  consistently across source, tests, and docs in the same pass, and the full test
  suite was re-run green after every one — renames were treated as refactors that
  need re-verification, not just find-and-replace.
