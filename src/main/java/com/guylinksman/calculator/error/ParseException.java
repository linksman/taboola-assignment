package com.guylinksman.calculator.error;

/** A line's tokens don't form valid grammar (includes out-of-range integer literals). */
public class ParseException extends CalculatorException {
    public ParseException(int line, String message) {
        super(line, message);
    }
}
