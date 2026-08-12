package com.guylinksman.calculator.ast;

/** No PERCENT_ASSIGN — only {@code += -= *= /=} are required compound-assignment operators (SPEC REQ-004). */
public enum AssignOperator {
    ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN
}
