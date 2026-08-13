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
import com.guylinksman.calculator.tokenizer.TokenType;
import com.guylinksman.calculator.tokenizer.Tokenizer;

import java.util.List;

/**
 * Hand-written recursive-descent parser for one statement's worth of tokens.
 *
 * <pre>
 * statement  := IDENT assignOp expression
 * expression := term (('+' | '-') term)*
 * term       := unary (('*' | '/' | '%') unary)*
 * unary      := ('-' | '+') unary | postfix
 * postfix    := primary ('++' | '--')?
 * primary    := NUMBER | IDENT | ('++' | '--') primary | '(' expression ')'
 * </pre>
 *
 * Both prefix and postfix {@code ++}/{@code --} additionally require their operand
 * to resolve to a variable reference (SPEC REQ-005) - a context-sensitive check
 * applied after parsing, same as real Java's "variable required" restriction,
 * rather than being baked into the grammar itself.
 */
public final class Parser {

    private final List<Token> tokens;
    private final int line;
    private int pos;

    private Parser(List<Token> tokens, int line) {
        this.tokens = tokens;
        this.line = line;
    }

    public static AssignmentStatement parseStatement(List<Token> tokens, int line) {
        Parser parser = new Parser(tokens, line);
        AssignmentStatement statement = parser.statement();
        parser.expect(TokenType.EOF, "unexpected trailing input");
        return statement;
    }

    private AssignmentStatement statement() {
        Token nameToken = expect(TokenType.IDENT, "expected a variable name at the start of the statement");
        AssignOperator op = assignOperator();
        Expr rhs = expression();
        return new AssignmentStatement(nameToken.text(), op, rhs, line);
    }

    private AssignOperator assignOperator() {
        Token token = advance();
        return switch (token.type()) {
            case ASSIGN -> AssignOperator.ASSIGN;
            case PLUS_ASSIGN -> AssignOperator.PLUS_ASSIGN;
            case MINUS_ASSIGN -> AssignOperator.MINUS_ASSIGN;
            case STAR_ASSIGN -> AssignOperator.STAR_ASSIGN;
            case SLASH_ASSIGN -> AssignOperator.SLASH_ASSIGN;
            default -> throw error("expected an assignment operator ('=', '+=', '-=', '*=', '/=') but found "
                    + describe(token));
        };
    }

    private Expr expression() {
        Expr left = term();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token op = advance();
            Expr right = term();
            left = new Expr.BinaryOp(left, op.type() == TokenType.PLUS ? BinaryOperator.ADD : BinaryOperator.SUB, right);
        }
        return left;
    }

    private Expr term() {
        Expr left = unary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            Token op = advance();
            Expr right = unary();
            BinaryOperator operator = switch (op.type()) {
                case STAR -> BinaryOperator.MUL;
                case SLASH -> BinaryOperator.DIV;
                case PERCENT -> BinaryOperator.MOD;
                default -> throw new IllegalStateException("unreachable");
            };
            left = new Expr.BinaryOp(left, operator, right);
        }
        return left;
    }

    private Expr unary() {
        if (check(TokenType.MINUS) && checkNext(TokenType.NUMBER)) {
            Token numberToken = peekAt(1);
            String text = numberToken.text();
            // JLS 3.10.1: the magnitude 2^63 (one past Long.MAX_VALUE, i.e. what
            // Long.parseLong alone would reject) is a legal literal only when it is
            // the *immediate* operand of unary minus - it's the only way to write
            // Long.MIN_VALUE at all, since there's no positive literal for it. Fold
            // it directly rather than negating a literal that can't parse on its own.
            if (text.indexOf('.') < 0 && !fitsInLong(text)) {
                try {
                    long value = Long.parseLong("-" + text);
                    advance(); // '-'
                    advance(); // the NUMBER token
                    // The folded literal is still a primary-level expression, so a
                    // trailing '++'/'--' must go through the same variable-required
                    // check postfix() applies everywhere else (e.g. `-9223372036854775808++`).
                    return applyPostfixIfPresent(new Expr.NumberLiteral(new Value.IntValue(value)));
                } catch (NumberFormatException ignored) {
                    // text is out of range even negated (e.g. -99999999999999999999) -
                    // fall through to the ordinary path below, which raises the right error.
                }
            }
        }
        if (check(TokenType.MINUS) || check(TokenType.PLUS)) {
            Token op = advance();
            Expr operand = unary();
            return new Expr.UnaryOp(op.type() == TokenType.MINUS ? UnaryOperator.MINUS : UnaryOperator.PLUS, operand);
        }
        return postfix();
    }

    private static boolean fitsInLong(String digits) {
        try {
            Long.parseLong(digits);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Expr postfix() {
        return applyPostfixIfPresent(primary());
    }

    /**
     * Shared by postfix() and the {@code Long.MIN_VALUE} literal fold in unary() -
     * both produce a primary-level expression that may be followed by a postfix
     * '++'/'--', which is only legal when that expression is a variable.
     */
    private Expr applyPostfixIfPresent(Expr expr) {
        if (check(TokenType.INCREMENT) || check(TokenType.DECREMENT)) {
            Expr.VariableRef varRef = requireVariable(expr);
            Token op = advance();
            return new Expr.PostfixIncDec(
                    op.type() == TokenType.INCREMENT ? IncDecOperator.INCREMENT : IncDecOperator.DECREMENT,
                    varRef.name());
        }
        return expr;
    }

    private Expr.VariableRef requireVariable(Expr expr) {
        if (expr instanceof Expr.VariableRef varRef) {
            return varRef;
        }
        throw error("'++'/'--' can only be applied to a variable");
    }

    private Expr primary() {
        Token token = peek();
        return switch (token.type()) {
            case NUMBER -> {
                advance();
                yield new Expr.NumberLiteral(parseNumber(token));
            }
            case INCREMENT, DECREMENT -> {
                advance();
                Expr.VariableRef varRef = requireVariable(primary());
                yield new Expr.PrefixIncDec(
                        token.type() == TokenType.INCREMENT ? IncDecOperator.INCREMENT : IncDecOperator.DECREMENT,
                        varRef.name());
            }
            case IDENT -> {
                advance();
                yield new Expr.VariableRef(token.text());
            }
            case LPAREN -> {
                advance();
                Expr inner = expression();
                expect(TokenType.RPAREN, "expected a closing ')'");
                yield inner;
            }
            default -> throw error("expected a number, variable, '(' or '++'/'--' but found " + describe(token));
        };
    }

    private Value parseNumber(Token token) {
        String text = token.text();
        if (text.indexOf('.') >= 0) {
            double value = Double.parseDouble(text);
            // JLS 3.10.2: a nonzero floating-point literal that rounds to infinity or
            // to zero is a compile-time error in real Java - only the literal itself is
            // restricted this way; Infinity/zero *produced by arithmetic* (e.g. 1.0 / 0)
            // is unaffected, since that never goes through this method.
            if (Double.isInfinite(value)) {
                throw new ParseException(line, "floating-point literal too large: " + text);
            }
            if (value == 0.0 && hasNonZeroDigit(text)) {
                throw new ParseException(line, "floating-point literal too small: " + text);
            }
            return new Value.FloatValue(value);
        }
        try {
            return new Value.IntValue(Long.parseLong(text));
        } catch (NumberFormatException e) {
            throw new ParseException(line, "integer literal out of range: " + text);
        }
    }

    private static boolean hasNonZeroDigit(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Reuses Tokenizer's canonical ASCII-digit definition rather than
            // re-deriving the '0'-'9' range independently.
            if (Tokenizer.isAsciiDigit(c) && c != '0') {
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private boolean checkNext(TokenType type) {
        return peekAt(1).type() == type;
    }

    private Token peek() {
        return peekAt(0);
    }

    /** Bounded lookahead shared by peek()/check()/checkNext() - past the end of the
     *  token stream (which always ends with EOF) simply keeps returning that EOF. */
    private Token peekAt(int offset) {
        int idx = pos + offset;
        return idx < tokens.size() ? tokens.get(idx) : tokens.get(tokens.size() - 1);
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private Token expect(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(message + ", found " + describe(peek()));
    }

    private ParseException error(String message) {
        return new ParseException(line, message);
    }

    private static String describe(Token token) {
        return token.type() == TokenType.EOF ? "end of input" : "'" + token.text() + "'";
    }
}
