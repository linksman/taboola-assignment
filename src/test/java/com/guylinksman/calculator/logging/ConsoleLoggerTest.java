package com.guylinksman.calculator.logging;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleLoggerTest {

    @Test
    void writesTheFormattedEventToTheGivenStream() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ConsoleLogger logger = new ConsoleLogger(new PrintStream(captured, true, StandardCharsets.UTF_8));

        FailureEvent event = new FailureEvent(Instant.now(), "ParseException", 1, "x = (5", "unexpected trailing input", "");
        logger.logFailure(event);

        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("ParseException"));
        assertTrue(output.contains("unexpected trailing input"));
    }
}
