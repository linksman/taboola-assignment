package com.guylinksman.calculator.logging;

import java.io.PrintStream;

/**
 * Writes failure logs to a {@link PrintStream} - stderr by default, keeping stdout
 * reserved exclusively for the calculator's own {@code (k=v,...)} output (SPEC
 * "Interface / API Requirements").
 */
public final class ConsoleLogger implements Logger {

    private final PrintStream out;

    public ConsoleLogger() {
        this(System.err);
    }

    public ConsoleLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void logFailure(FailureEvent event) {
        out.print(LogFormatter.format(event));
    }
}
