package com.guylinksman.calculator.logging;

import java.util.List;

/**
 * Fans one failure event out to several loggers - console + file today. Adding an
 * HTTP-backed {@link Logger} later means writing that one class and passing it in
 * here; nothing about {@link ConsoleLogger} or {@link FileLogger} needs to change.
 */
public final class MultiLogger implements Logger {

    private final List<Logger> loggers;

    public MultiLogger(Logger... loggers) {
        this.loggers = List.of(loggers);
    }

    @Override
    public void logFailure(FailureEvent event) {
        for (Logger logger : loggers) {
            logger.logFailure(event);
        }
    }
}
