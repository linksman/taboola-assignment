package com.guylinksman.calculator.logging;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFormatterTest {

    @Test
    void formatIncludesExceptionTypeAndMessage() {
        FailureEvent event = new FailureEvent(Instant.parse("2026-01-01T00:00:00Z"),
                "EvalException", 3, "x = 5 / 0", "line 3: / by zero", "java.lang.ArithmeticException: / by zero");

        String formatted = LogFormatter.format(event);

        assertTrue(formatted.contains("EvalException"));
        assertTrue(formatted.contains("line 3: / by zero"));
        assertTrue(formatted.contains("source: x = 5 / 0"));
        assertTrue(formatted.contains("stack trace:"));
        assertTrue(formatted.contains("java.lang.ArithmeticException"));
    }

    @Test
    void formatOmitsEmptySourceAndStackTraceSections() {
        FailureEvent event = new FailureEvent(Instant.now(), "IllegalStateException", 0, "", "boom", "");

        String formatted = LogFormatter.format(event);

        assertFalse(formatted.contains("source:"));
        assertFalse(formatted.contains("stack trace:"));
    }
}
