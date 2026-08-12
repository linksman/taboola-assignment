package com.guylinksman.calculator.error;

/** A statement is syntactically valid but fails at evaluation time (e.g. undefined variable, division by zero). */
public class EvalException extends CalculatorException {
    public EvalException(int line, String message) {
        super(line, message);
    }
}
