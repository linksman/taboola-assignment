package com.guylinksman.calculator.eval;

import com.guylinksman.calculator.ast.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatterTest {

    private final Formatter formatter = new Formatter();

    @Test
    void intValuePrintsAsPlainDigits() {
        assertEquals("10", formatter.formatValue(new Value.IntValue(10)));
        assertEquals("-2", formatter.formatValue(new Value.IntValue(-2)));
    }

    @Test
    void wholeNumberFloatPrintsWithoutTrailingZero() {
        assertEquals("10", formatter.formatValue(new Value.FloatValue(10.0)));
        assertEquals("80", formatter.formatValue(new Value.FloatValue(80.0)));
    }

    @Test
    void fractionalFloatPrintsWithDecimal() {
        assertEquals("6.28", formatter.formatValue(new Value.FloatValue(6.28)));
    }

    @Test
    void specialFloatsPrintAsJavaWords() {
        assertEquals("Infinity", formatter.formatValue(new Value.FloatValue(1.0 / 0.0)));
        assertEquals("-Infinity", formatter.formatValue(new Value.FloatValue(-1.0 / 0.0)));
        assertEquals("NaN", formatter.formatValue(new Value.FloatValue(0.0 / 0.0)));
    }

    @Test
    void joinsEntriesInInsertionOrder() {
        Environment env = new Environment();
        env.set("i", new Value.IntValue(82));
        env.set("j", new Value.IntValue(1));
        env.set("x", new Value.IntValue(6));
        env.set("y", new Value.IntValue(80));
        assertEquals("(i=82,j=1,x=6,y=80)", formatter.format(env));
    }

    @Test
    void emptyEnvironmentFormatsAsEmptyParens() {
        assertEquals("()", formatter.format(new Environment()));
    }
}
