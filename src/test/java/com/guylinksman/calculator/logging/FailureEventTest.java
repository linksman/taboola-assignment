package com.guylinksman.calculator.logging;

import com.guylinksman.calculator.error.EvalException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureEventTest {

    @Test
    void calculatorExceptionEventCapturesTheOffendingSourceLine() {
        EvalException ex = new EvalException(2, "undefined variable 'y'");
        FailureEvent event = FailureEvent.of(ex, List.of("x = 1", "z = y + 1"));

        assertEquals("EvalException", event.exceptionType());
        assertEquals(2, event.line());
        assertEquals("z = y + 1", event.sourceLine());
        assertEquals(ex.getMessage(), event.message());
        assertTrue(event.stackTrace().contains("EvalException"));
    }

    @Test
    void calculatorExceptionEventHandlesLineOutOfRangeGracefully() {
        EvalException ex = new EvalException(99, "undefined variable 'y'");
        FailureEvent event = FailureEvent.of(ex, List.of("x = 1"));

        assertEquals("", event.sourceLine());
    }

    @Test
    void genericThrowableEventHasNoLineOrSource() {
        RuntimeException ex = new IllegalStateException("boom");
        FailureEvent event = FailureEvent.of(ex, List.of("x = 1"));

        assertEquals("IllegalStateException", event.exceptionType());
        assertEquals(0, event.line());
        assertEquals("", event.sourceLine());
        assertEquals("boom", event.message());
    }

    @Test
    void genericThrowableEventHandlesNullMessage() {
        RuntimeException ex = new NullPointerException();
        FailureEvent event = FailureEvent.of(ex, List.of());

        assertNotNull(event.message());
        assertEquals("null", event.message());
    }
}
