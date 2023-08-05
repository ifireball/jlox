package org.korren.jlox;

import java.util.ArrayList;
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

    // program -> declaration* EOF
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while(!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    // declaration -> varDeclaration | statement
    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();

            return null;
        }
    }

    // varDeclaration -> "var" identifier ( "=" expression )?
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    // Throw away tokens until we find something the looks like the beginning of the next statement
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

    // statement -> expressionStatement | ifStatement | printStatement | whileStatement | block
    private Stmt statement() {
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    // whileStatement -> "while" "(" expression ")" statement
    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    // ifStatement -> "if" "(" expression ")" statement ( "else" statement )?
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    // block -> "{" declaration* "}"
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    // printStatement -> "print" expression ";"
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    // expressionStatement -> expression ";"
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    // expression -> continuation
    private Expr expression() {
        return continuation();
    }

    // Production template:
    //
    // binaryProduction -> missingLeft | next ( "<one of: operators>" next )*
    private Expr binaryProduction(Production next, TokenType... operators) {
        Expr expr = next.production();

        while (match(operators)) {
            Token operator = previous();
            Expr right = next.production();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Production template:
    //
    // binaryProductionWithErrorDetection -> missingLeft | binaryProduction
    // missingLeft                        -> "<one of: operators>" next -> error
    private Expr binaryProductionWithErrorDetection(Production next, TokenType... operators) {
        if (match(operators)) {
            Token operator = previous();
            next.production();
            throw error(operator, "Missing left operand");
        }
        return binaryProduction(next, operators);
    }

    // continuation -> assignment ( "," assignment )*
    private Expr continuation() {
        return binaryProductionWithErrorDetection(this::assignment, COMMA);
    }

    // assignment -> identifier "=" assignment | ternary
    private Expr assignment() {
        Expr expr = ternary();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    // ternary -> equality ( "?" ( expression ) ":" ternary )?
    private Expr ternary() {
        Expr expr = or();

        if (match(QUESTION_MARK)) {
            Expr trueBranch = expression();
            consume(COLON, "Expect ':' in a ternary operator expression");
            Expr falseBranch = ternary();
            expr = new Expr.Ternary(expr, trueBranch, falseBranch);
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    // equality -> comparison ( ( "!=" | "==" ) comparison )*
    private Expr equality() {
        return binaryProductionWithErrorDetection(this::comparison, BANG_EQUAL, EQUAL_EQUAL);
    }

    // comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )+
    private Expr comparison() {
        return binaryProductionWithErrorDetection(this::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL);
    }

    // term -> factor ( ( "-" | "+" ) factor )*
    private Expr term() {
        return binaryProduction(this::factor, MINUS, PLUS);
    }

    // factor -> unary ( "/" | "*" ) factor )*
    private Expr factor() {
        return binaryProductionWithErrorDetection(this::unary, SLASH, STAR);
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

    // primary -> ( "false" | "true" | "nil" | number | string | "(" expression ")" | identifier )
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

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
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
