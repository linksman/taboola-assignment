package com.guylinksman.calculator.logging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MultiLoggerTest {

    private static final class RecordingLogger implements Logger {
        final List<FailureEvent> received = new ArrayList<>();

        @Override
        public void logFailure(FailureEvent event) {
            received.add(event);
        }
    }

    @Test
    void forwardsTheSameEventToEveryLogger() {
        RecordingLogger first = new RecordingLogger();
        RecordingLogger second = new RecordingLogger();
        MultiLogger multiLogger = new MultiLogger(first, second);

        FailureEvent event = new FailureEvent(Instant.now(), "EvalException", 1, "x = 5 / 0", "line 1: / by zero", "");
        multiLogger.logFailure(event);

        assertEquals(1, first.received.size());
        assertEquals(1, second.received.size());
        assertSame(event, first.received.get(0));
        assertSame(event, second.received.get(0));
    }

    @Test
    void supportsZeroLoggersWithoutFailing() {
        new MultiLogger().logFailure(new FailureEvent(Instant.now(), "EvalException", 1, "", "x", ""));
    }
}
