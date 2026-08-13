package com.guylinksman.calculator.eval;

import com.guylinksman.calculator.ast.AssignmentStatement;
import com.guylinksman.calculator.ast.Value;
import com.guylinksman.calculator.error.EvalException;
import com.guylinksman.calculator.parser.Parser;
import com.guylinksman.calculator.tokenizer.Tokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatorTest {

    private final Tokenizer tokenizer = new Tokenizer();
    private final Evaluator evaluator = new Evaluator();

    private void run(Environment env, String line) {
        run(env, line, 1);
    }

    private void run(Environment env, String line, int lineNumber) {
        AssignmentStatement statement = Parser.parseStatement(tokenizer.tokenize(line, lineNumber), lineNumber);
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
    void compoundAssignmentReadsLeftOperandBeforeRhsMutatesIt() {
        // JLS 15.26.2: the LHS is saved BEFORE the RHS is evaluated. i += i++ must
        // use the pre-increment value of i on the left, not the post-increment one.
        Environment env = new Environment();
        run(env, "i = 1");
        run(env, "i += i++");
        assertEquals(new Value.IntValue(2), valueOf(env, "i"));
    }

    @Test
    void compoundAssignmentReadsLeftOperandBeforePrefixMutatesIt() {
        Environment env = new Environment();
        run(env, "i = 1");
        run(env, "i += ++i");
        assertEquals(new Value.IntValue(3), valueOf(env, "i"));
    }

    @ParameterizedTest(name = "[{index}] i={0}; i {1} {2}; -> i={3}")
    @MethodSource("compoundAssignmentSelfMutationCases")
    void compoundAssignmentSelfMutationMatrix(long start, String op, String rhsExpr, long expected) {
        Environment env = new Environment();
        run(env, "i = " + start);
        run(env, "i " + op + " " + rhsExpr);
        assertEquals(new Value.IntValue(expected), valueOf(env, "i"));
    }

    static Stream<Arguments> compoundAssignmentSelfMutationCases() {
        return Stream.of(
                // postfix i++ on the RHS: it returns i's pre-mutation value, but that's
                // irrelevant here - what matters is the *saved LHS* is also pre-mutation.
                Arguments.of(1L, "+=", "i++", 2L),
                Arguments.of(1L, "-=", "i++", 0L),
                Arguments.of(1L, "*=", "i++", 1L),
                Arguments.of(1L, "/=", "i++", 1L),
                // prefix ++i on the RHS: it returns i's post-mutation value.
                Arguments.of(1L, "+=", "++i", 3L),
                Arguments.of(1L, "-=", "++i", -1L),
                Arguments.of(1L, "*=", "++i", 2L),
                Arguments.of(1L, "/=", "++i", 0L),
                // postfix i-- on the RHS.
                Arguments.of(5L, "+=", "i--", 10L),
                Arguments.of(5L, "-=", "i--", 0L),
                Arguments.of(5L, "*=", "i--", 25L),
                Arguments.of(5L, "/=", "i--", 1L),
                // prefix --i on the RHS.
                Arguments.of(5L, "+=", "--i", 9L),
                Arguments.of(5L, "-=", "--i", 1L),
                Arguments.of(5L, "*=", "--i", 20L),
                Arguments.of(5L, "/=", "--i", 1L));
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
    void prefixIncDecOnParenthesizedVariableMutatesIt() {
        Environment env = new Environment();
        run(env, "i = 1");
        run(env, "x = ++(i)");
        assertEquals(new Value.IntValue(2), valueOf(env, "i"));
        assertEquals(new Value.IntValue(2), valueOf(env, "x"));

        run(env, "y = --((i))");
        assertEquals(new Value.IntValue(1), valueOf(env, "i"));
        assertEquals(new Value.IntValue(1), valueOf(env, "y"));
    }

    @Test
    void longMinValueLiteralEvaluatesCorrectly() {
        Environment env = new Environment();
        run(env, "x = -9223372036854775808");
        assertEquals(new Value.IntValue(Long.MIN_VALUE), valueOf(env, "x"));
    }

    @Test
    void doubleNegatedLongMinValueWrapsBackToItself() {
        // The inner '-9223372036854775808' folds to the MIN_VALUE literal (special
        // case); the outer '-' is then an ordinary runtime negation of that constant,
        // which overflows straight back to itself - exactly like real Java `long`.
        Environment env = new Environment();
        run(env, "x = - -9223372036854775808");
        assertEquals(new Value.IntValue(Long.MIN_VALUE), valueOf(env, "x"));
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
    void infinityProducedByArithmeticIsStillValid() {
        // Only the *literal* rejects overflow/underflow (JLS 3.10.2); Infinity computed
        // at runtime via ordinary division remains perfectly valid, e.g. 1.0 / 0.
        Environment env = new Environment();
        run(env, "x = 1.0 / 0");
        assertEquals(new Value.FloatValue(Double.POSITIVE_INFINITY), valueOf(env, "x"));
    }

    @Test
    void floatModuloByZeroDoesNotThrow() {
        Environment env = new Environment();
        run(env, "x = 5.0 % 0");
        assertEquals(Double.NaN, ((Value.FloatValue) valueOf(env, "x")).raw());
    }
}
