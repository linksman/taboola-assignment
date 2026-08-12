package com.guylinksman.calculator.error;

/** Base of the calculator's exception hierarchy; always carries the 1-based input line number. */
public abstract class CalculatorException extends RuntimeException {

    private final int line;

    protected CalculatorException(int line, String message) {
        super(message);
        this.line = line;
    }

    public int line() {
        return line;
    }

    @Override
    public String getMessage() {
        return "line " + line + ": " + super.getMessage();
    }
}
