package com.guylinksman.calculator.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLoggerTest {

    @Test
    void createsParentDirectoriesAndWritesTheEvent(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("logs").resolve("failures.log");
        FileLogger logger = new FileLogger(logFile);

        logger.logFailure(new FailureEvent(Instant.now(), "EvalException", 1, "x = 5 / 0", "line 1: / by zero", ""));

        assertTrue(Files.exists(logFile));
        String content = Files.readString(logFile);
        assertTrue(content.contains("EvalException"));
        assertTrue(content.contains("line 1: / by zero"));
    }

    @Test
    void appendsRatherThanOverwritingOnSubsequentFailures(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("failures.log");
        FileLogger logger = new FileLogger(logFile);

        logger.logFailure(new FailureEvent(Instant.now(), "ParseException", 1, "a", "first failure", ""));
        logger.logFailure(new FailureEvent(Instant.now(), "EvalException", 2, "b", "second failure", ""));

        String content = Files.readString(logFile);
        assertTrue(content.contains("first failure"));
        assertTrue(content.contains("second failure"));
        assertEquals(2, content.lines().filter(line -> line.contains("Exception:")).count());
    }
}
