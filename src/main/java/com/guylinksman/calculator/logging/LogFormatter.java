package com.guylinksman.calculator.logging;

import java.time.format.DateTimeFormatter;

/** One rendering shared by every {@link Logger} implementation, so console/file/(later)
 *  HTTP output all agree on what a failure log entry looks like. */
public final class LogFormatter {

    private LogFormatter() {
    }

    public static String format(FailureEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(DateTimeFormatter.ISO_INSTANT.format(event.timestamp())).append("] ")
                .append(event.exceptionType()).append(": ").append(event.message())
                .append(System.lineSeparator());
        if (!event.sourceLine().isEmpty()) {
            sb.append("    source: ").append(event.sourceLine()).append(System.lineSeparator());
        }
        if (!event.stackTrace().isEmpty()) {
            sb.append("    stack trace:").append(System.lineSeparator());
            event.stackTrace().lines()
                    .forEach(traceLine -> sb.append("        ").append(traceLine).append(System.lineSeparator()));
        }
        return sb.toString();
    }
}
