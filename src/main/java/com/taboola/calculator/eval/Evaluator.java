package com.taboola.calculator.eval;

import com.taboola.calculator.ast.AssignOperator;
import com.taboola.calculator.ast.AssignmentStatement;
import com.taboola.calculator.ast.BinaryOperator;
import com.taboola.calculator.ast.Expr;
import com.taboola.calculator.ast.IncDecOperator;
import com.taboola.calculator.ast.UnaryOperator;
import com.taboola.calculator.ast.Value;
import com.taboola.calculator.error.EvalException;

/**
 * Walks one statement's AST against a shared {@link Environment}, using native
 * Java {@code long}/{@code double} arithmetic throughout so overflow wraparound
 * and IEEE-754 float behavior come from the JVM itself (DESIGN "Numeric
 * Promotion & Arithmetic Dispatch").
 */
public final class Evaluator {

    public void execute(AssignmentStatement statement, Environment env) {
        int line = statement.line();
        Value rhsValue = evaluate(statement.rhs(), env, line);
        String name = statement.varName();

        if (statement.op() == AssignOperator.ASSIGN) {
            // Plain '=' adopts the RHS's kind outright (SPEC "Deliberate Deviations").
            env.set(name, rhsValue);
            return;
        }

        requireDefined(env, name, line);
        Value current = env.get(name);
        Value combined = applyBinary(current, toBinaryOperator(statement.op()), rhsValue, line);
        // Compound assignment narrows back to the variable's current kind, matching
        // Java's implicit compound-assignment cast (JLS 5.2) - see DESIGN REQ-004.
        env.set(name, narrowToKind(current, combined));
    }

    private Value evaluate(Expr expr, Environment env, int line) {
        if (expr instanceof Expr.NumberLiteral literal) {
            return literal.value();
        }
        if (expr instanceof Expr.VariableRef ref) {
            requireDefined(env, ref.name(), line);
            return env.get(ref.name());
        }
        if (expr instanceof Expr.BinaryOp binaryOp) {
            Value left = evaluate(binaryOp.left(), env, line);
            Value right = evaluate(binaryOp.right(), env, line);
            return applyBinary(left, binaryOp.op(), right, line);
        }
        if (expr instanceof Expr.UnaryOp unaryOp) {
            Value value = evaluate(unaryOp.operand(), env, line);
            return applyUnary(unaryOp.op(), value);
        }
        if (expr instanceof Expr.PrefixIncDec incDec) {
            requireDefined(env, incDec.varName(), line);
            Value updated = step(env.get(incDec.varName()), incDec.op());
            env.set(incDec.varName(), updated);
            return updated;
        }
        if (expr instanceof Expr.PostfixIncDec incDec) {
            requireDefined(env, incDec.varName(), line);
            Value original = env.get(incDec.varName());
            env.set(incDec.varName(), step(original, incDec.op()));
            return original;
        }
        throw new IllegalStateException("unreachable Expr: " + expr);
    }

    private Value applyUnary(UnaryOperator op, Value value) {
        if (op == UnaryOperator.PLUS) {
            return value;
        }
        if (value instanceof Value.IntValue iv) {
            return new Value.IntValue(-iv.raw());
        }
        return new Value.FloatValue(-((Value.FloatValue) value).raw());
    }

    private Value step(Value current, IncDecOperator op) {
        int delta = op == IncDecOperator.INCREMENT ? 1 : -1;
        if (current instanceof Value.IntValue iv) {
            return new Value.IntValue(iv.raw() + delta);
        }
        return new Value.FloatValue(((Value.FloatValue) current).raw() + delta);
    }

    private Value applyBinary(Value left, BinaryOperator op, Value right, int line) {
        if (left instanceof Value.FloatValue || right instanceof Value.FloatValue) {
            double l = toDouble(left);
            double r = toDouble(right);
            return new Value.FloatValue(switch (op) {
                case ADD -> l + r;
                case SUB -> l - r;
                case MUL -> l * r;
                case DIV -> l / r;
                case MOD -> l % r;
            });
        }
        long l = ((Value.IntValue) left).raw();
        long r = ((Value.IntValue) right).raw();
        try {
            return new Value.IntValue(switch (op) {
                case ADD -> l + r;
                case SUB -> l - r;
                case MUL -> l * r;
                case DIV -> l / r;
                case MOD -> l % r;
            });
        } catch (ArithmeticException e) {
            throw new EvalException(line, e.getMessage());
        }
    }

    private Value narrowToKind(Value previousKind, Value combined) {
        if (previousKind instanceof Value.IntValue && combined instanceof Value.FloatValue fv) {
            return new Value.IntValue((long) fv.raw());
        }
        return combined;
    }

    private double toDouble(Value value) {
        if (value instanceof Value.IntValue iv) {
            return iv.raw();
        }
        return ((Value.FloatValue) value).raw();
    }

    private BinaryOperator toBinaryOperator(AssignOperator op) {
        return switch (op) {
            case PLUS_ASSIGN -> BinaryOperator.ADD;
            case MINUS_ASSIGN -> BinaryOperator.SUB;
            case STAR_ASSIGN -> BinaryOperator.MUL;
            case SLASH_ASSIGN -> BinaryOperator.DIV;
            case ASSIGN -> throw new IllegalStateException("ASSIGN has no binary-operator equivalent");
        };
    }

    private void requireDefined(Environment env, String name, int line) {
        if (!env.has(name)) {
            throw new EvalException(line, "undefined variable '" + name + "'");
        }
    }
}
