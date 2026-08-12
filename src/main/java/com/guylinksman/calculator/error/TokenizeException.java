package com.taboola.calculator.error;

/** A line contains a character the lexer doesn't recognize. */
public class LexException extends CalculatorException {
    public LexException(int line, String message) {
        super(line, message);
    }
}
