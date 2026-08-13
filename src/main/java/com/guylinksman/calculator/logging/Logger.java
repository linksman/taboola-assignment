package com.guylinksman.calculator.logging;

/**
 * Records a failure. Deliberately scoped to just that - not a general
 * info/debug/warn logging framework, since that's not what was asked for.
 * Multiple implementations can be combined via {@link MultiLogger} (e.g. console
 * + file today, with an HTTP-backed implementation addable later with no changes
 * to the others).
 */
public interface Logger {
    void logFailure(FailureEvent event);
}
