package com.taboola.calculator.ast;

public record AssignmentStatement(String varName, AssignOperator op, Expr rhs, int line) {
}
