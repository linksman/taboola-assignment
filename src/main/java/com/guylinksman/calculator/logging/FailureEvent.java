package com.guylinksman.calculator.logging;

import com.guylinksman.calculator.error.CalculatorException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

/**
 * Everything worth knowing about one failure, captured once so every {@link Logger}
 * implementation renders the same data instead of each re-deriving it.
 *
 * @param line       1-based input line the failure is attributed to; {@code 0} if
 *                   not applicable (e.g. a failure with no specific source line).
 * @param sourceLine the offending input line's actual text, or {@code ""} if unknown.
 */
public record FailureEvent(
        Instant timestamp,
        String exceptionType,
        int line,
        String sourceLine,
        String message,
        String stackTrace) {

    /** For a {@link CalculatorException}, which always knows which line it failed on. */
    public static FailureEvent of(CalculatorException e, List<String> inputLines) {
        int line = e.line();
        String source = (line >= 1 && line <= inputLines.size()) ? inputLines.get(line - 1) : "";
        return new FailureEvent(Instant.now(), e.getClass().getSimpleName(), line, source, e.getMessage(), stackTraceOf(e));
    }

    /** For any other failure (an unexpected bug), which has no specific input line. */
    public static FailureEvent of(Throwable e, List<String> inputLines) {
        return new FailureEvent(Instant.now(), e.getClass().getSimpleName(), 0, "", String.valueOf(e.getMessage()), stackTraceOf(e));
    }

    private static String stackTraceOf(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
