# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A text-based calculator: reads a series of assignment statements (one per line,
Java-like syntax) from stdin and prints the final value of every variable that
was assigned. The core design goal is **Java fidelity** — wherever the language
overlaps with real Java (arithmetic, precedence, numeric promotion, overflow,
division-by-zero), results must match exactly what `javac`/the JVM would
produce, using native `long`/`double` operators rather than hand-rolled
arithmetic.

**`README.md` is the primary reference for this repo** — it covers build/test/run
commands (including how to run a single test), input syntax, the numeric
promotion and compound-assignment-narrowing rules, the two deliberate
deviations from Java fidelity, error handling (fail-fast), the concurrency
model, output formatting, and the full package-by-package architecture with a
pipeline diagram. Read it before making non-trivial changes.

`docs/SPEC.md`, `docs/DESIGN.md`, `docs/PLAN.md`, and `docs/DISCUSSION.md` go
deeper still — requirements, architecture/data-structure rationale, the
build-plan history, and a simplicity/OOP/concurrency/optimization writeup,
respectively. Most "why is it done this way" questions are already answered
there.
