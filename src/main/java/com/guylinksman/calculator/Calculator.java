package com.guylinksman.calculator;

import com.guylinksman.calculator.ast.AssignmentStatement;
import com.guylinksman.calculator.eval.Environment;
import com.guylinksman.calculator.eval.Evaluator;
import com.guylinksman.calculator.eval.Formatter;
import com.guylinksman.calculator.parser.Parser;
import com.guylinksman.calculator.tokenizer.Token;
import com.guylinksman.calculator.tokenizer.Tokenizer;

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
        Tokenizer tokenizer = new Tokenizer();
        Evaluator evaluator = new Evaluator();
        Environment env = new Environment();

        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            List<Token> tokens = tokenizer.tokenize(line, lineNumber);
            AssignmentStatement statement = Parser.parseStatement(tokens, lineNumber);
            evaluator.execute(statement, env);
        }

        return new Formatter().format(env);
    }
}
