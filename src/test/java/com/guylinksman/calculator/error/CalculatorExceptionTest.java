package com.guylinksman.calculator.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorExceptionTest {

    @Test
    void evalExceptionFormatsMessageWithLineNumber() {
        EvalException ex = new EvalException(3, "undefined variable 'y'");

        assertEquals(3, ex.line());
        assertEquals("line 3: undefined variable 'y'", ex.getMessage());
    }

    @Test
    void parseExceptionFormatsMessageWithLineNumber() {
        ParseException ex = new ParseException(5, "unexpected token");

        assertEquals(5, ex.line());
        assertEquals("line 5: unexpected token", ex.getMessage());
    }

    @Test
    void tokenizeExceptionFormatsMessageWithLineNumber() {
        TokenizeException ex = new TokenizeException(1, "unrecognized character '#'");

        assertEquals(1, ex.line());
        assertEquals("line 1: unrecognized character '#'", ex.getMessage());
    }
}
