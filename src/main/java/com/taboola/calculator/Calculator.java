package com.taboola.calculator;

import com.taboola.calculator.ast.AssignmentStatement;
import com.taboola.calculator.eval.Environment;
import com.taboola.calculator.eval.Evaluator;
import com.taboola.calculator.eval.Formatter;
import com.taboola.calculator.lexer.Lexer;
import com.taboola.calculator.lexer.Token;
import com.taboola.calculator.parser.Parser;

import java.util.List;

/**
 * Library entry point: evaluates a series of assignment statements (SPEC REQ-006)
 * and returns the final {@code (k1=v1,k2=v2,...)} output (REQ-007). Independent of
 * stdin/stdout so it's directly unit/integration testable; {@code cli.Main} is the
 * thin process wrapper around this.
 */
public final class Calculator {

    private Calculator() {
    }

    public static String run(List<String> lines) {
        Lexer lexer = new Lexer();
        Evaluator evaluator = new Evaluator();
        Environment env = new Environment();

        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            List<Token> tokens = lexer.tokenize(line, lineNumber);
            AssignmentStatement statement = Parser.parseStatement(tokens, lineNumber);
            evaluator.execute(statement, env);
        }

        return new Formatter().format(env);
    }
}
