package com.guylinksman.calculator.ast;

/**
 * The two numeric kinds the calculator supports, each backed by a real Java
 * primitive type so their arithmetic behaves exactly like Java's own.
 */
public sealed interface Value {

    /** @param raw a Java {@code long} — arithmetic on it wraps on overflow exactly like real Java. */
    record IntValue(long raw) implements Value {
    }

    /** @param raw a Java {@code double} (IEEE-754). */
    record FloatValue(double raw) implements Value {
    }
}
