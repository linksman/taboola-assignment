package com.guylinksman.calculator.tokenizer;

import com.guylinksman.calculator.error.TokenizeException;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns one input line into a list of tokens, ending with an EOF sentinel.
 * Only tokenizes: numeric literals are captured as raw text, not range-checked
 * or converted here (that happens in the parser).
 */
public final class Tokenizer {

    public List<Token> tokenize(String line, int lineNumber) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = line.length();

        while (i < n) {
            char c = line.charAt(i);
            int start = i;

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (isAsciiDigit(c)) {
                i++;
                while (i < n && isAsciiDigit(line.charAt(i))) {
                    i++;
                }
                if (i < n && line.charAt(i) == '.') {
                    if (i + 1 < n && isAsciiDigit(line.charAt(i + 1))) {
                        i++;
                        while (i < n && isAsciiDigit(line.charAt(i))) {
                            i++;
                        }
                    } else {
                        throw new TokenizeException(lineNumber,
                                "expected a digit after '.' at column " + (i + 1));
                    }
                }
                tokens.add(new Token(TokenType.NUMBER, line.substring(start, i), start + 1));
                continue;
            }

            if (isIdentifierStart(c)) {
                i++;
                while (i < n && isIdentifierPart(line.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(TokenType.IDENT, line.substring(start, i), start + 1));
                continue;
            }

            switch (c) {
                case '+' -> {
                    if (peek(line, i + 1) == '+') {
                        tokens.add(new Token(TokenType.INCREMENT, "++", start + 1));
                        i += 2;
                    } else if (peek(line, i + 1) == '=') {
                        tokens.add(new Token(TokenType.PLUS_ASSIGN, "+=", start + 1));
                        i += 2;
                    } else {
                        tokens.add(new Token(TokenType.PLUS, "+", start + 1));
                        i += 1;
                    }
                }
                case '-' -> {
                    if (peek(line, i + 1) == '-') {
                        tokens.add(new Token(TokenType.DECREMENT, "--", start + 1));
                        i += 2;
                    } else if (peek(line, i + 1) == '=') {
                        tokens.add(new Token(TokenType.MINUS_ASSIGN, "-=", start + 1));
                        i += 2;
                    } else {
                        tokens.add(new Token(TokenType.MINUS, "-", start + 1));
                        i += 1;
                    }
                }
                case '*' -> {
                    if (peek(line, i + 1) == '=') {
                        tokens.add(new Token(TokenType.STAR_ASSIGN, "*=", start + 1));
                        i += 2;
                    } else {
                        tokens.add(new Token(TokenType.STAR, "*", start + 1));
                        i += 1;
                    }
                }
                case '/' -> {
                    if (peek(line, i + 1) == '=') {
                        tokens.add(new Token(TokenType.SLASH_ASSIGN, "/=", start + 1));
                        i += 2;
                    } else {
                        tokens.add(new Token(TokenType.SLASH, "/", start + 1));
                        i += 1;
                    }
                }
                case '%' -> {
                    if (peek(line, i + 1) == '=') {
                        tokens.add(new Token(TokenType.PERCENT_ASSIGN, "%=", start + 1));
                        i += 2;
                    } else {
                        tokens.add(new Token(TokenType.PERCENT, "%", start + 1));
                        i += 1;
                    }
                }
                case '=' -> {
                    tokens.add(new Token(TokenType.ASSIGN, "=", start + 1));
                    i += 1;
                }
                case '(' -> {
                    tokens.add(new Token(TokenType.LPAREN, "(", start + 1));
                    i += 1;
                }
                case ')' -> {
                    tokens.add(new Token(TokenType.RPAREN, ")", start + 1));
                    i += 1;
                }
                default -> throw new TokenizeException(lineNumber,
                        "unexpected character '" + c + "' at column " + (start + 1));
            }
        }

        tokens.add(new Token(TokenType.EOF, "", n + 1));
        return tokens;
    }

    private static char peek(String line, int index) {
        return index < line.length() ? line.charAt(index) : '\0';
    }

    // SPEC "Data Requirements": variable names are [a-zA-Z_][a-zA-Z0-9_]* - ASCII
    // only. Character.isDigit/isLetter/isLetterOrDigit are Unicode-aware and would
    // accept things like Arabic-Indic digits or accented letters, which are neither
    // documented nor round-trippable through Long.parseLong/Double.parseDouble.

    /** Public so Parser can reuse this exact definition (e.g. for underflow detection
     *  on already-tokenized NUMBER text) instead of re-deriving its own digit range. */
    public static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isAsciiDigit(c);
    }
}
