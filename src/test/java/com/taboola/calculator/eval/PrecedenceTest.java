package com.taboola.calculator.eval;

import com.taboola.calculator.ast.AssignmentStatement;
import com.taboola.calculator.ast.Value;
import com.taboola.calculator.lexer.Lexer;
import com.taboola.calculator.parser.Parser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Precedence/associativity matrix (PLAN Milestone 2 step 15); expected values are hand-computed. */
class PrecedenceTest {

    private static Value evaluate(String expression) {
        Lexer lexer = new Lexer();
        Environment env = new Environment();
        AssignmentStatement statement = Parser.parseStatement(lexer.tokenize("result = " + expression, 1), 1);
        new Evaluator().execute(statement, env);
        return env.get("result");
    }

    @ParameterizedTest(name = "[{index}] {0} = {1}")
    @MethodSource("cases")
    void evaluatesToExpectedValue(String expression, Value expected) {
        assertEquals(expected, evaluate(expression));
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                // multiplicative binds tighter than additive
                Arguments.of("2 + 3 * 4", new Value.IntValue(14)),
                Arguments.of("(2 + 3) * 4", new Value.IntValue(20)),
                // left-associativity within a precedence level
                Arguments.of("10 - 3 - 2", new Value.IntValue(5)),
                Arguments.of("2 * 3 + 4 * 5", new Value.IntValue(26)),
                Arguments.of("2 + 3 % 4", new Value.IntValue(5)),
                Arguments.of("((2 + 3) * (4 - 1))", new Value.IntValue(15)),
                Arguments.of("-2 + 3 * -4", new Value.IntValue(-14)),
                // same shapes, with a float operand forcing promotion throughout
                Arguments.of("2 + 3.0 * 4", new Value.FloatValue(14.0)),
                Arguments.of("(2 + 3) * 4.0", new Value.FloatValue(20.0)),
                Arguments.of("10.0 - 3 - 2", new Value.FloatValue(5.0)),
                Arguments.of("2 * 3 + 4 * 5.0", new Value.FloatValue(26.0)),
                Arguments.of("2 + 3.5 % 4", new Value.FloatValue(5.5)),
                Arguments.of("-2.0 + 3 * -4", new Value.FloatValue(-14.0)));
    }
}
