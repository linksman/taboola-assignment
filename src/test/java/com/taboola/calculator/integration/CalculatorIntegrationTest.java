package com.taboola.calculator.integration;

import com.taboola.calculator.Calculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** End-to-end coverage of Calculator.run(...) against the SPEC examples; the primary demo/regression suite. */
class CalculatorIntegrationTest {

    @Test
    void specExample() {
        assertEquals("(i=82,j=1,x=6,y=80)", Calculator.run(List.of(
                "i = 0",
                "j = ++i",
                "x = i++ + 5",
                "y = (5 + 3) * 10",
                "i += y")));
    }

    @Test
    void overflowWraparound() {
        assertEquals("(big=-9223372036854775808)", Calculator.run(List.of(
                "big = 9223372036854775807 + 1")));
    }

    @Test
    void floatArithmetic() {
        assertEquals("(f=6.28,mix=6.5)", Calculator.run(List.of(
                "f = 3.14 * 2",
                "mix = 5 + 1.5")));
    }

    @Test
    void wholeNumberFloatDisplaysWithoutTrailingZero() {
        assertEquals("(whole=10)", Calculator.run(List.of(
                "whole = 8.0 + 2.0")));
    }

    @Test
    void compoundAssignmentNarrowing() {
        assertEquals("(i=7)", Calculator.run(List.of(
                "i = 5",
                "i += 2.5")));
    }

    @Test
    void moduloOperator() {
        assertEquals("(x=1)", Calculator.run(List.of(
                "x = 7 % 3")));
    }

    @Test
    void unaryMinus() {
        assertEquals("(x=-2)", Calculator.run(List.of(
                "x = -5 + 3")));
    }
}
