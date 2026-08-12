package com.guylinksman.calculator.eval;

import com.guylinksman.calculator.ast.Value;

import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * Renders an {@link Environment} as {@code (k1=v1,k2=v2,...)} in first-appearance
 * order. Floats use calculator-style display: a whole-number float prints without
 * a trailing {@code .0} (SPEC "Deliberate Deviations" / REQ-007) - the value's
 * kind is unaffected, only this rendering step drops it.
 */
public final class Formatter {

    public String format(Environment env) {
        return env.asMap().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + formatValue(entry.getValue()))
                .collect(Collectors.joining(",", "(", ")"));
    }

    String formatValue(Value value) {
        if (value instanceof Value.IntValue iv) {
            return Long.toString(iv.raw());
        }
        double raw = ((Value.FloatValue) value).raw();
        if (Double.isNaN(raw)) {
            return "NaN";
        }
        if (Double.isInfinite(raw)) {
            return raw > 0 ? "Infinity" : "-Infinity";
        }
        if (raw == Math.rint(raw)) {
            // Route through Double.toString's canonical shortest form (via BigDecimal.valueOf)
            // rather than the exact binary value, so e.g. 8.0 + 2.0 prints "10", not scientific
            // notation or a binary-imprecision artifact.
            return BigDecimal.valueOf(raw).toBigInteger().toString();
        }
        return Double.toString(raw);
    }
}
