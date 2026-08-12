package com.taboola.calculator.eval;

import com.taboola.calculator.ast.AssignmentStatement;
import com.taboola.calculator.ast.Value;
import com.taboola.calculator.error.EvalException;
import com.taboola.calculator.lexer.Lexer;
import com.taboola.calculator.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatorTest {

    private final Lexer lexer = new Lexer();
    private final Evaluator evaluator = new Evaluator();

    private void run(Environment env, String line) {
        run(env, line, 1);
    }

    private void run(Environment env, String line, int lineNumber) {
        AssignmentStatement statement = Parser.parseStatement(lexer.tokenize(line, lineNumber), lineNumber);
        evaluator.execute(statement, env);
    }

    private Value valueOf(Environment env, String name) {
        return env.get(name);
    }

    @Test
    void specExampleProducesDocumentedIntermediateState() {
        Environment env = new Environment();

        run(env, "i = 0");
        assertEquals(new Value.IntValue(0), valueOf(env, "i"));

        run(env, "j = ++i");
        assertEquals(new Value.IntValue(1), valueOf(env, "i"));
        assertEquals(new Value.IntValue(1), valueOf(env, "j"));

        run(env, "x = i++ + 5");
        assertEquals(new Value.IntValue(2), valueOf(env, "i"));
        assertEquals(new Value.IntValue(6), valueOf(env, "x"));

        run(env, "y = (5 + 3) * 10");
        assertEquals(new Value.IntValue(80), valueOf(env, "y"));

        run(env, "i += y");
        assertEquals(new Value.IntValue(82), valueOf(env, "i"));
    }

    @Test
    void integerAdditionWrapsOnOverflowLikeJavaLong() {
        Environment env = new Environment();
        run(env, "big = " + Long.MAX_VALUE);
        run(env, "big += 1");
        assertEquals(new Value.IntValue(Long.MIN_VALUE), valueOf(env, "big"));
    }

    @Test
    void mixedIntAndFloatOperandsPromoteToFloat() {
        Environment env = new Environment();
        run(env, "x = 5");
        run(env, "y = x + 1.5");
        assertEquals(new Value.FloatValue(6.5), valueOf(env, "y"));
    }

    @Test
    void incDecOnFloatVariableStepsByOnePointZero() {
        Environment env = new Environment();
        run(env, "x = 2.5");
        run(env, "y = ++x");
        assertEquals(new Value.FloatValue(3.5), valueOf(env, "x"));
        assertEquals(new Value.FloatValue(3.5), valueOf(env, "y"));

        run(env, "z = x--");
        assertEquals(new Value.FloatValue(3.5), valueOf(env, "z"));
        assertEquals(new Value.FloatValue(2.5), valueOf(env, "x"));
    }

    @Test
    void compoundAssignmentNarrowsBackToIntegerKind() {
        Environment env = new Environment();
        run(env, "i = 5");
        run(env, "i += 2.5");
        assertEquals(new Value.IntValue(7), valueOf(env, "i"));
    }

    @Test
    void moduloMatchesJavaSignOfDividendSemantics() {
        Environment env = new Environment();
        run(env, "a = 7 % 3");
        assertEquals(new Value.IntValue(1), valueOf(env, "a"));

        run(env, "b = -7 % 3");
        assertEquals(new Value.IntValue(-1), valueOf(env, "b"));

        run(env, "c = 7.5 % 2");
        assertEquals(new Value.FloatValue(1.5), valueOf(env, "c"));
    }

    @Test
    void unaryMinusNegatesOperand() {
        Environment env = new Environment();
        run(env, "x = -5 + 3");
        assertEquals(new Value.IntValue(-2), valueOf(env, "x"));

        run(env, "y = -(2 + 3)");
        assertEquals(new Value.IntValue(-5), valueOf(env, "y"));
    }

    @Test
    void readingUndefinedVariableThrowsWithCorrectLine() {
        Environment env = new Environment();
        EvalException ex = assertThrows(EvalException.class, () -> run(env, "x = y + 1", 7));
        assertEquals(7, ex.line());
        assertTrue(ex.getMessage().contains("y"), "message should mention the undefined variable: " + ex.getMessage());
    }

    @Test
    void compoundAssignmentToUndefinedVariableThrows() {
        Environment env = new Environment();
        assertThrows(EvalException.class, () -> run(env, "i += 1"));
    }

    @Test
    void integerDivisionByZeroThrows() {
        Environment env = new Environment();
        assertThrows(EvalException.class, () -> run(env, "x = 5 / 0"));
    }

    @Test
    void integerModuloByZeroThrows() {
        Environment env = new Environment();
        assertThrows(EvalException.class, () -> run(env, "x = 5 % 0"));
    }

    @Test
    void floatDivisionByZeroDoesNotThrow() {
        Environment env = new Environment();
        run(env, "x = 5.0 / 0");
        assertEquals(new Value.FloatValue(Double.POSITIVE_INFINITY), valueOf(env, "x"));

        run(env, "y = 0.0 / 0.0");
        assertEquals(Double.NaN, ((Value.FloatValue) valueOf(env, "y")).raw());
    }

    @Test
    void floatModuloByZeroDoesNotThrow() {
        Environment env = new Environment();
        run(env, "x = 5.0 % 0");
        assertEquals(Double.NaN, ((Value.FloatValue) valueOf(env, "x")).raw());
    }
}
