package com.guylinksman.calculator.error;

/** A line contains a character the tokenizer doesn't recognize. */
public class TokenizeException extends CalculatorException {
    public TokenizeException(int line, String message) {
        super(line, message);
    }
}
