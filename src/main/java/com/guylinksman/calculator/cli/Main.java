package com.guylinksman.calculator.cli;

import com.guylinksman.calculator.Calculator;
import com.guylinksman.calculator.error.CalculatorException;
import com.guylinksman.calculator.logging.ConsoleLogger;
import com.guylinksman.calculator.logging.FailureEvent;
import com.guylinksman.calculator.logging.FileLogger;
import com.guylinksman.calculator.logging.Logger;
import com.guylinksman.calculator.logging.MultiLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads statements from stdin, prints the result to stdout (SPEC "Interface / API Requirements"). */
public final class Main {

    private static final Path FAILURE_LOG_FILE = Path.of("logs", "calculator-failures.log");

    public static void main(String[] args) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        Logger logger = new MultiLogger(new ConsoleLogger(), new FileLogger(FAILURE_LOG_FILE));

        try {
            System.out.println(Calculator.run(lines));
        } catch (CalculatorException e) {
            // The concise "line N: message" line is the documented CLI contract
            // (SPEC "Error Handling") - printed first and unchanged. The failure
            // log entry that follows is additional detail, not a replacement for it.
            System.err.println(e.getMessage());
            logger.logFailure(FailureEvent.of(e, lines));
            System.exit(1);
        } catch (RuntimeException e) {
            // A bug, not a user-input error: no line to point at, so there's no
            // concise message to print - the failure log is the only record of it.
            System.err.println("an unexpected error occurred - see " + FAILURE_LOG_FILE + " for details");
            logger.logFailure(FailureEvent.of(e, lines));
            System.exit(1);
        }
    }
}
