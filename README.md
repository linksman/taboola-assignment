# taboola-assignment

A text-based calculator: reads a series of assignment statements (one per line,
Java-like syntax) and prints the final value of every variable that was assigned.

See `docs/SPEC.md`, `docs/DESIGN.md`, and `docs/PLAN.md` for the full requirements,
architecture, and build plan behind this implementation.

## Build & test

```bash
mvn test       # run the full test suite
mvn package     # also produce target/calculator-1.0-SNAPSHOT.jar
```

## Run

```bash
java -jar target/calculator-1.0-SNAPSHOT.jar
```

The CLI reads statements from **stdin** until EOF (Ctrl+D if typing interactively),
then prints one result line. Pipe input directly, or redirect from a file:

```bash
printf 'i = 0\nj = ++i\nx = i++ + 5\ny = (5 + 3) * 10\ni += y\n' \
  | java -jar target/calculator-1.0-SNAPSHOT.jar
# (i=82,j=1,x=6,y=80)

java -jar target/calculator-1.0-SNAPSHOT.jar < input.txt
```

On error, a line-numbered message is printed to **stderr** and the process exits
with a non-zero status; stdout stays empty.

## Input syntax

One assignment per line: `var = expression` or `var <op>= expression` where
`<op>` is `+ - * /`. Expressions support `+ - * / %`, parentheses, unary `+`/`-`,
and prefix/postfix `++`/`--` on variables — the same precedence and side-effect
rules as the equivalent Java code.

## Output format

`(var1=val1,var2=val2,...)`, variables in the order they first appeared in the
input. Two numeric kinds are tracked internally, both backed by real Java
primitives so arithmetic (including overflow and division-by-zero behavior)
matches what the same code would do in Java:

- **Integers** — Java `long` (64-bit signed), including overflow wraparound.
- **Floats** — Java `double` (IEEE-754).

The one deliberate difference from raw Java output: a float with no fractional
part prints without a trailing `.0` — calculator-style. `8.0 + 2.0` prints as
`(x=10)`, not `(x=10.0)`; `6.28` still prints with its decimal. This is display
only — the value's underlying kind, and all further arithmetic on it, still
follows `double` rules (e.g. dividing it by zero still yields `Infinity`, not an
error). See `docs/SPEC.md` ("Deliberate Deviations") for the full rationale.
