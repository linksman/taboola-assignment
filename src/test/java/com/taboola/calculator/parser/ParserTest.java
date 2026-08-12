package com.taboola.calculator.parser;

import com.taboola.calculator.ast.AssignOperator;
import com.taboola.calculator.ast.AssignmentStatement;
import com.taboola.calculator.ast.BinaryOperator;
import com.taboola.calculator.ast.Expr;
import com.taboola.calculator.ast.IncDecOperator;
import com.taboola.calculator.ast.UnaryOperator;
import com.taboola.calculator.ast.Value;
import com.taboola.calculator.error.ParseException;
import com.taboola.calculator.lexer.Lexer;
import com.taboola.calculator.lexer.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserTest {

    private final Lexer lexer = new Lexer();

    private AssignmentStatement parse(String line) {
        List<Token> tokens = lexer.tokenize(line, 1);
        return Parser.parseStatement(tokens, 1);
    }

    @Test
    void simpleAssignment() {
        assertEquals(
                new AssignmentStatement("i", AssignOperator.ASSIGN,
                        new Expr.NumberLiteral(new Value.IntValue(0)), 1),
                parse("i = 0"));
    }

    @Test
    void prefixIncrementAssignment() {
        assertEquals(
                new AssignmentStatement("j", AssignOperator.ASSIGN,
                        new Expr.PrefixIncDec(IncDecOperator.INCREMENT, "i"), 1),
                parse("j = ++i"));
    }

    @Test
    void postfixIncrementPlusLiteral() {
        assertEquals(
                new AssignmentStatement("x", AssignOperator.ASSIGN,
                        new Expr.BinaryOp(
                                new Expr.PostfixIncDec(IncDecOperator.INCREMENT, "i"),
                                BinaryOperator.ADD,
                                new Expr.NumberLiteral(new Value.IntValue(5))),
                        1),
                parse("x = i++ + 5"));
    }

    @Test
    void parensOverridePrecedence() {
        Expr fiveePlusThree = new Expr.BinaryOp(
                new Expr.NumberLiteral(new Value.IntValue(5)),
                BinaryOperator.ADD,
                new Expr.NumberLiteral(new Value.IntValue(3)));
        assertEquals(
                new AssignmentStatement("y", AssignOperator.ASSIGN,
                        new Expr.BinaryOp(fiveePlusThree, BinaryOperator.MUL,
                                new Expr.NumberLiteral(new Value.IntValue(10))),
                        1),
                parse("y = (5 + 3) * 10"));
    }

    @Test
    void compoundAssignmentToVariable() {
        assertEquals(
                new AssignmentStatement("i", AssignOperator.PLUS_ASSIGN,
                        new Expr.VariableRef("y"), 1),
                parse("i += y"));
    }

    @Test
    void moduloIsABinaryOp() {
        assertEquals(
                new AssignmentStatement("x", AssignOperator.ASSIGN,
                        new Expr.BinaryOp(
                                new Expr.NumberLiteral(new Value.IntValue(7)),
                                BinaryOperator.MOD,
                                new Expr.NumberLiteral(new Value.IntValue(3))),
                        1),
                parse("x = 7 % 3"));
    }

    @Test
    void unaryMinusAtExpressionStart() {
        assertEquals(
                new AssignmentStatement("x", AssignOperator.ASSIGN,
                        new Expr.BinaryOp(
                                new Expr.UnaryOp(UnaryOperator.MINUS, new Expr.NumberLiteral(new Value.IntValue(5))),
                                BinaryOperator.ADD,
                                new Expr.NumberLiteral(new Value.IntValue(3))),
                        1),
                parse("x = -5 + 3"));
    }

    @Test
    void unaryMinusDistinguishedFromBinaryMinus() {
        assertEquals(
                new AssignmentStatement("x", AssignOperator.ASSIGN,
                        new Expr.BinaryOp(
                                new Expr.NumberLiteral(new Value.IntValue(3)),
                                BinaryOperator.SUB,
                                new Expr.UnaryOp(UnaryOperator.MINUS, new Expr.NumberLiteral(new Value.IntValue(5)))),
                        1),
                parse("x = 3 - -5"));
    }

    @Test
    void longMaxValueLiteralIsValid() {
        assertEquals(
                new AssignmentStatement("x", AssignOperator.ASSIGN,
                        new Expr.NumberLiteral(new Value.IntValue(Long.MAX_VALUE)), 1),
                parse("x = 9223372036854775807"));
    }

    @Test
    void literalBeyondLongRangeIsParseException() {
        ParseException ex = assertThrows(ParseException.class, () -> parse("x = 99999999999999999999999999999"));
        assertEquals(1, ex.line());
    }

    @Test
    void decimalLiteralIsFloatValue() {
        assertEquals(
                new AssignmentStatement("x", AssignOperator.ASSIGN,
                        new Expr.BinaryOp(
                                new Expr.NumberLiteral(new Value.FloatValue(3.14)),
                                BinaryOperator.MUL,
                                new Expr.NumberLiteral(new Value.IntValue(2))),
                        1),
                parse("x = 3.14 * 2"));
    }

    @Test
    void allFourIncDecForms() {
        assertEquals(new Expr.PrefixIncDec(IncDecOperator.INCREMENT, "i"), parse("x = ++i").rhs());
        assertEquals(new Expr.PostfixIncDec(IncDecOperator.INCREMENT, "i"), parse("x = i++").rhs());
        assertEquals(new Expr.PrefixIncDec(IncDecOperator.DECREMENT, "i"), parse("x = --i").rhs());
        assertEquals(new Expr.PostfixIncDec(IncDecOperator.DECREMENT, "i"), parse("x = i--").rhs());
    }

    @Test
    void incDecOnNonVariableIsRejected() {
        // Must not crash the parser (Milestone 1 requirement); dedicated error-message
        // coverage for this case is added in Milestone 2.
        assertThrows(ParseException.class, () -> parse("x = (x + 1)++"));
    }
}
