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
            logger.logFailure(FailureEvent.of(e, lines));
            System.exit(1);
        } catch (RuntimeException e){ 
            logger.logFailure(FailureEvent.of(e, lines));
            System.exit(1);
        }
    }
}
