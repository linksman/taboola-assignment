package com.taboola.calculator.ast;

public sealed interface Expr {

    record NumberLiteral(Value value) implements Expr {
    }

    record VariableRef(String name) implements Expr {
    }

    record BinaryOp(Expr left, BinaryOperator op, Expr right) implements Expr {
    }

    record UnaryOp(UnaryOperator op, Expr operand) implements Expr {
    }

    /** {@code ++x} / {@code --x} — reads and mutates {@code varName} before yielding the new value. */
    record PrefixIncDec(IncDecOperator op, String varName) implements Expr {
    }

    /** {@code x++} / {@code x--} — mutates {@code varName} but yields the pre-mutation value. */
    record PostfixIncDec(IncDecOperator op, String varName) implements Expr {
    }
}
