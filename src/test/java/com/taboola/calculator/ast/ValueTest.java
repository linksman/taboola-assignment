package com.taboola.calculator.ast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ValueTest {

    @Test
    void intValueEqualityAndToString() {
        assertEquals(new Value.IntValue(82), new Value.IntValue(82));
        assertNotEquals(new Value.IntValue(82), new Value.IntValue(1));
        assertEquals("IntValue[raw=82]", new Value.IntValue(82).toString());
    }

    @Test
    void floatValueEqualityAndToString() {
        assertEquals(new Value.FloatValue(6.28), new Value.FloatValue(6.28));
        assertNotEquals(new Value.FloatValue(6.28), new Value.FloatValue(1.0));
        assertEquals("FloatValue[raw=6.28]", new Value.FloatValue(6.28).toString());
    }
}
