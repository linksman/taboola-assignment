package com.guylinksman.calculator.parser;

import com.guylinksman.calculator.ast.AssignOperator;
import com.guylinksman.calculator.ast.AssignmentStatement;
import com.guylinksman.calculator.ast.BinaryOperator;
import com.guylinksman.calculator.ast.Expr;
import com.guylinksman.calculator.ast.IncDecOperator;
import com.guylinksman.calculator.ast.UnaryOperator;
import com.guylinksman.calculator.ast.Value;
import com.guylinksman.calculator.error.ParseException;
import com.guylinksman.calculator.tokenizer.Token;
import com.guylinksman.calculator.tokenizer.Tokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserTest {

    private final Tokenizer tokenizer = new Tokenizer();

    private AssignmentStatement parse(String line) {
        List<Token> tokens = tokenizer.tokenize(line, 1);
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
    void longMinValueLiteralIsValidAsImmediateUnaryMinusOperand() {
        // JLS 3.10.1: 9223372036854775808 (2^63) is only a legal literal directly
        // after unary minus - it folds straight to a single Long.MIN_VALUE literal,
        // not a UnaryOp wrapping a NumberLiteral (there's no positive literal for it).
        assertEquals(
                new AssignmentStatement("x", AssignOperator.ASSIGN,
                        new Expr.NumberLiteral(new Value.IntValue(Long.MIN_VALUE)), 1),
                parse("x = -9223372036854775808"));
    }

    @Test
    void ordinaryNegativeLiteralsStillWrapInUnaryOp() {
        // The Long.MIN_VALUE special case must not change parsing of everyday
        // negative literals - they still go through ordinary unary-minus wrapping.
        assertEquals(
                new AssignmentStatement("x", AssignOperator.ASSIGN,
                        new Expr.UnaryOp(UnaryOperator.MINUS, new Expr.NumberLiteral(new Value.IntValue(5))), 1),
                parse("x = -5"));
    }

    @Test
    void magnitudeOneBeyondLongMinValueIsStillParseException() {
        // -9223372036854775809 has no valid long representation at all, negated or not.
        ParseException ex = assertThrows(ParseException.class, () -> parse("x = -9223372036854775809"));
        assertEquals(1, ex.line());
    }

    @Test
    void postfixIncDecOnFoldedLongMinValueLiteralIsRejectedWithThePreciseMessage() {
        // The folded Long.MIN_VALUE literal is still a primary-level expression, not
        // a variable, so a trailing '++'/'--' must be rejected the same way it would
        // be after any other non-variable primary, e.g. `(x + 1)++`.
        ParseException ex = assertThrows(ParseException.class, () -> parse("x = -9223372036854775808++"));
        assertEquals("line 1: '++'/'--' can only be applied to a variable", ex.getMessage());
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
    void floatLiteralThatOverflowsToInfinityIsParseException() {
        // JLS 3.10.2: a nonzero float literal too large to represent is a compile-time
        // error in Java, not a silent round to Infinity - a 400-digit literal overflows
        // double's ~1.8e308 range.
        String hugeLiteral = "1" + "0".repeat(400) + ".0";
        ParseException ex = assertThrows(ParseException.class, () -> parse("x = " + hugeLiteral));
        assertEquals(1, ex.line());
    }

    @Test
    void floatLiteralThatUnderflowsToZeroIsParseException() {
        // Same rule, other direction: a nonzero literal too small to represent
        // (smaller than Double.MIN_VALUE, ~4.9e-324) is also a compile-time error,
        // not a silent round to 0.0.
        String tinyLiteral = "0." + "0".repeat(400) + "1";
        ParseException ex = assertThrows(ParseException.class, () -> parse("x = " + tinyLiteral));
        assertEquals(1, ex.line());
    }

    @Test
    void literalZeroIsStillValid() {
        // Actual zero (no nonzero digit) must not be mistaken for underflow.
        assertEquals(
                new AssignmentStatement("x", AssignOperator.ASSIGN,
                        new Expr.NumberLiteral(new Value.FloatValue(0.0)), 1),
                parse("x = 0.0"));
    }

    @Test
    void allFourIncDecForms() {
        assertEquals(new Expr.PrefixIncDec(IncDecOperator.INCREMENT, "i"), parse("x = ++i").rhs());
        assertEquals(new Expr.PostfixIncDec(IncDecOperator.INCREMENT, "i"), parse("x = i++").rhs());
        assertEquals(new Expr.PrefixIncDec(IncDecOperator.DECREMENT, "i"), parse("x = --i").rhs());
        assertEquals(new Expr.PostfixIncDec(IncDecOperator.DECREMENT, "i"), parse("x = i--").rhs());
    }

    @Test
    void prefixIncDecAcceptsAParenthesizedVariable() {
        // (i) resolves to a VariableRef just like a bare identifier does, and Java
        // itself accepts ++(i) - postfix (i)++ already worked before this fix.
        assertEquals(new Expr.PrefixIncDec(IncDecOperator.INCREMENT, "i"), parse("x = ++(i)").rhs());
        assertEquals(new Expr.PrefixIncDec(IncDecOperator.DECREMENT, "i"), parse("x = --((i))").rhs());
    }

    @ParameterizedTest(name = "[{index}] {1}: \"{0}\"")
    @MethodSource("malformedStatements")
    void rejectsMalformedSyntaxWithCorrectLine(String input, String description) {
        List<Token> tokens = tokenizer.tokenize(input, 3);
        ParseException ex = assertThrows(ParseException.class, () -> Parser.parseStatement(tokens, 3),
                description + " should be rejected: \"" + input + "\"");
        assertEquals(3, ex.line());
    }

    static Stream<Arguments> malformedStatements() {
        return Stream.of(
                Arguments.of("x = (5 + 3", "unmatched opening paren"),
                Arguments.of("x = 5 + 3)", "unmatched closing paren"),
                Arguments.of("x = 5 +", "missing right operand"),
                Arguments.of("x = * 5", "missing left operand"),
                Arguments.of("5 = x", "invalid assignment target (literal on LHS)"),
                Arguments.of("x + y = 5", "invalid assignment target (expression on LHS)"),
                Arguments.of("x = (x + 1)++", "'++' applied to a non-variable"),
                Arguments.of("x = (x + 1)--", "'--' applied to a non-variable"),
                Arguments.of("x = ++(i + 1)", "prefix '++' applied to a non-variable, even parenthesized"),
                Arguments.of("x =", "missing right-hand side entirely"),
                Arguments.of("x", "missing assignment operator and expression"));
    }
}
