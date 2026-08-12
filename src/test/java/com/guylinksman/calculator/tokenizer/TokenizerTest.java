package com.taboola.calculator.lexer;

import com.taboola.calculator.error.LexException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LexerTest {

    private final Lexer lexer = new Lexer();

    private static void assertTokens(List<Token> actual, Object... typeTextPairs) {
        assertEquals(typeTextPairs.length / 2 + 1, actual.size(), "token count (incl. EOF) for " + actual);
        for (int i = 0; i < typeTextPairs.length; i += 2) {
            TokenType expectedType = (TokenType) typeTextPairs[i];
            String expectedText = (String) typeTextPairs[i + 1];
            Token token = actual.get(i / 2);
            assertEquals(expectedType, token.type(), "type at index " + (i / 2));
            assertEquals(expectedText, token.text(), "text at index " + (i / 2));
        }
        assertEquals(TokenType.EOF, actual.get(actual.size() - 1).type());
    }

    @Test
    void tokenizesSimpleAssignment() {
        assertTokens(lexer.tokenize("i = 0", 1),
                TokenType.IDENT, "i",
                TokenType.ASSIGN, "=",
                TokenType.NUMBER, "0");
    }

    @Test
    void tokenizesPrefixIncrement() {
        assertTokens(lexer.tokenize("j = ++i", 2),
                TokenType.IDENT, "j",
                TokenType.ASSIGN, "=",
                TokenType.INCREMENT, "++",
                TokenType.IDENT, "i");
    }

    @Test
    void tokenizesPostfixIncrementPlusLiteral() {
        assertTokens(lexer.tokenize("x = i++ + 5", 3),
                TokenType.IDENT, "x",
                TokenType.ASSIGN, "=",
                TokenType.IDENT, "i",
                TokenType.INCREMENT, "++",
                TokenType.PLUS, "+",
                TokenType.NUMBER, "5");
    }

    @Test
    void tokenizesParenthesizedExpression() {
        assertTokens(lexer.tokenize("y = (5 + 3) * 10", 4),
                TokenType.IDENT, "y",
                TokenType.ASSIGN, "=",
                TokenType.LPAREN, "(",
                TokenType.NUMBER, "5",
                TokenType.PLUS, "+",
                TokenType.NUMBER, "3",
                TokenType.RPAREN, ")",
                TokenType.STAR, "*",
                TokenType.NUMBER, "10");
    }

    @Test
    void tokenizesCompoundAssignment() {
        assertTokens(lexer.tokenize("i += y", 5),
                TokenType.IDENT, "i",
                TokenType.PLUS_ASSIGN, "+=",
                TokenType.IDENT, "y");
    }

    @Test
    void tokenizesOversizedIntegerLiteralAsRawText() {
        // The lexer never range-checks magnitude; a 30-digit literal is still just one NUMBER token.
        assertTokens(lexer.tokenize("99999999999999999999999999999", 1),
                TokenType.NUMBER, "99999999999999999999999999999");
    }

    @Test
    void tokenizesDecimalLiteralAndMultiplication() {
        assertTokens(lexer.tokenize("3.14 * 2", 1),
                TokenType.NUMBER, "3.14",
                TokenType.STAR, "*",
                TokenType.NUMBER, "2");
    }

    @Test
    void tokenizesModulo() {
        assertTokens(lexer.tokenize("7 % 3", 1),
                TokenType.NUMBER, "7",
                TokenType.PERCENT, "%",
                TokenType.NUMBER, "3");
    }

    @Test
    void tokenizesUnaryMinus() {
        assertTokens(lexer.tokenize("-5 + 3", 1),
                TokenType.MINUS, "-",
                TokenType.NUMBER, "5",
                TokenType.PLUS, "+",
                TokenType.NUMBER, "3");
    }

    @Test
    void ignoresIrregularWhitespace() {
        assertTokens(lexer.tokenize("i  =  0", 1),
                TokenType.IDENT, "i",
                TokenType.ASSIGN, "=",
                TokenType.NUMBER, "0");
    }

    @Test
    void tokenizesAllCompoundAndPostfixOperators() {
        assertTokens(lexer.tokenize("a -= 1", 1),
                TokenType.IDENT, "a",
                TokenType.MINUS_ASSIGN, "-=",
                TokenType.NUMBER, "1");
        assertTokens(lexer.tokenize("a *= 1", 1),
                TokenType.IDENT, "a",
                TokenType.STAR_ASSIGN, "*=",
                TokenType.NUMBER, "1");
        assertTokens(lexer.tokenize("a /= 1", 1),
                TokenType.IDENT, "a",
                TokenType.SLASH_ASSIGN, "/=",
                TokenType.NUMBER, "1");
        assertTokens(lexer.tokenize("a--", 1),
                TokenType.IDENT, "a",
                TokenType.DECREMENT, "--");
    }

    @Test
    void rejectsUnknownCharacter() {
        LexException ex = assertThrows(LexException.class, () -> lexer.tokenize("i = 0 & 1", 7));
        assertEquals(7, ex.line());
    }

    @Test
    void rejectsDotWithoutFollowingDigit() {
        assertThrows(LexException.class, () -> lexer.tokenize("x = 5.", 1));
    }
}
