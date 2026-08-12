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

            if (Character.isDigit(c)) {
                i++;
                while (i < n && Character.isDigit(line.charAt(i))) {
                    i++;
                }
                if (i < n && line.charAt(i) == '.') {
                    if (i + 1 < n && Character.isDigit(line.charAt(i + 1))) {
                        i++;
                        while (i < n && Character.isDigit(line.charAt(i))) {
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

            if (Character.isLetter(c) || c == '_') {
                i++;
                while (i < n && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) {
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
}
