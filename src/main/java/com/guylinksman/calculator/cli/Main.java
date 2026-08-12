package com.guylinksman.calculator.cli;

import com.guylinksman.calculator.Calculator;
import com.guylinksman.calculator.error.CalculatorException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads statements from stdin, prints the result to stdout (SPEC "Interface / API Requirements"). */
public final class Main {

    public static void main(String[] args) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        try {
            System.out.println(Calculator.run(lines));
        } catch (CalculatorException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
