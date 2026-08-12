package com.taboola.calculator.parser;

import com.taboola.calculator.ast.AssignOperator;
import com.taboola.calculator.ast.AssignmentStatement;
import com.taboola.calculator.ast.BinaryOperator;
import com.taboola.calculator.ast.Expr;
import com.taboola.calculator.ast.IncDecOperator;
import com.taboola.calculator.ast.UnaryOperator;
import com.taboola.calculator.ast.Value;
import com.taboola.calculator.error.ParseException;
import com.taboola.calculator.lexer.Token;
import com.taboola.calculator.lexer.TokenType;

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
 * primary    := NUMBER | IDENT | '++' IDENT | '--' IDENT | '(' expression ')'
 * </pre>
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
            default -> throw error("expected an assignment operator ('=', '+=', '-=', '*=', '/=') but found '"
                    + token.text() + "'");
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
        if (check(TokenType.MINUS) || check(TokenType.PLUS)) {
            Token op = advance();
            Expr operand = unary();
            return new Expr.UnaryOp(op.type() == TokenType.MINUS ? UnaryOperator.MINUS : UnaryOperator.PLUS, operand);
        }
        return postfix();
    }

    private Expr postfix() {
        Expr expr = primary();
        if (check(TokenType.INCREMENT) || check(TokenType.DECREMENT)) {
            if (!(expr instanceof Expr.VariableRef varRef)) {
                throw error("'++'/'--' can only be applied to a variable");
            }
            Token op = advance();
            return new Expr.PostfixIncDec(
                    op.type() == TokenType.INCREMENT ? IncDecOperator.INCREMENT : IncDecOperator.DECREMENT,
                    varRef.name());
        }
        return expr;
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
                Token identToken = expect(TokenType.IDENT, "'++'/'--' can only be applied to a variable");
                yield new Expr.PrefixIncDec(
                        token.type() == TokenType.INCREMENT ? IncDecOperator.INCREMENT : IncDecOperator.DECREMENT,
                        identToken.text());
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
            default -> throw error("expected a number, variable, '(' or '++'/'--' but found '" + token.text() + "'");
        };
    }

    private Value parseNumber(Token token) {
        String text = token.text();
        if (text.indexOf('.') >= 0) {
            return new Value.FloatValue(Double.parseDouble(text));
        }
        try {
            return new Value.IntValue(Long.parseLong(text));
        } catch (NumberFormatException e) {
            throw new ParseException(line, "integer literal out of range: " + text);
        }
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private Token expect(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(message + ", found '" + peek().text() + "'");
    }

    private ParseException error(String message) {
        return new ParseException(line, message);
    }
}
