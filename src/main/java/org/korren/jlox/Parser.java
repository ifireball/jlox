package org.korren.jlox;

import java.util.List;

import static org.korren.jlox.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    private interface Production {
        Expr production();
    }

    Parser(List<Token> tokens) {
        this.tokens= tokens;
    }

    Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    // expression -> continuation
    private Expr expression() {
        return continuation();
    }

    // Production template:
    //
    // binaryProduction -> next ( "<one of: operators>" next )*
    private Expr binaryProduction(Production next, TokenType... operators) {
        Expr expr = next.production();

        while (match(operators)) {
            Token operator = previous();
            Expr right = next.production();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // continuation -> ternary ( "," ternary )*
    private Expr continuation() {
        return binaryProduction(this::ternary, COMMA);
    }

    // ternary -> equality ( "?" ( expression ) ":" ternary )?
    private Expr ternary() {
        Expr expr = equality();

        if (match(QUESTION_MARK)) {
            Expr trueBranch = expression();
            consume(COLON, "Expect ':' in a ternary operator expression");
            Expr falseBranch = ternary();
            expr = new Expr.Ternary(expr, trueBranch, falseBranch);
        }

        return expr;
    }

    // equality -> comparison ( ( "!=" | "==" ) comparison )*
    private Expr equality() {
        return binaryProduction(this::comparison, BANG_EQUAL, EQUAL_EQUAL);
    }

    // comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )+
    private Expr comparison() {
        return binaryProduction(this::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL);
    }

    // term -> factor ( ( "-" | "+" ) factor )*
    private Expr term() {
        return binaryProduction(this::factor, MINUS, PLUS);
    }

    // factor -> unary ( "/" | "*" ) factor )*
    private Expr factor() {
        return binaryProduction(this::unary, SLASH, STAR);
    }

    // unary -> ( ( "!" | "-" ) unary | primary )
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    // primary -> ( "false" | "true" | "nil" | number | string | "(" expression ")" )
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }
}
