package com.guylinksman.calculator.tokenizer;

/** @param column 1-based position of the token's first character within its source line. */
public record Token(TokenType type, String text, int column) {
}
