package com.guylinksman.calculator.ast;

public record AssignmentStatement(String varName, AssignOperator op, Expr rhs, int line) {
}
