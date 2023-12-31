package org.korren.jlox;

import java.util.ArrayList;
import java.util.Arrays;
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

    // declaration -> classDeclaration | function | varDeclaration | statement
    private Stmt declaration() {
        try {
            if (match(CLASS)) return classDeclaration();
            // To differentiate between a function definition statement and a lambda expression, we check if 'fun'
            // is followed by a name
            if (check(FUN) && checkNext(IDENTIFIER)) {
                advance();
                return function("function");
            }
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();

            return null;
        }
    }

    // classDeclaration -> "class" identifier ( "<" identifier )? "{" ( "class"? function )* "}"
    // function is without the "fun" keyword.
    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");

        Expr.Variable superclass = null;
        if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name");
            superclass = new Expr.Variable(previous());
        }

        consume(LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();
        List<Stmt.Function> classMethods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            if (match(CLASS)) {
                classMethods.add(function("method"));
            } else {
                methods.add(function("method"));
            }
        }

        consume(RIGHT_BRACE, "Expect '}' after class body");

        return new Stmt.Class(name, superclass, methods, classMethods);
    }

    // function -> "fun" identifier "(" parameters? ")" block
    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name");
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name");
        List<Token> parameters = parameters();
        consume(RIGHT_PAREN, "Expect ')' after parameters");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");

        List<Stmt> body = block();

        return new Stmt.Function(name, parameters, body);
    }

    // parameters -> identifier ( "," identifier )*
    private List<Token> parameters() {
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more then 255 parameters");
                }

                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        return parameters;
    }

    // varDeclaration -> "var" identifier ( "=" expression )? ";"
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
                case BREAK:
                case CONTINUE:
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

    // statement -> expressionStatement | forStatement | ifStatement | printStatement | returnStatement |
    //                whileStatement | breakStatement | continueStatement | block
    private Stmt statement() {
        if (match(BREAK)) return breakStatement();
        if (match(CONTINUE)) return continueStatement();
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value");
        return new Stmt.Return(keyword, value);
    }

    // breakStatement = "break" ";"
    private Stmt breakStatement() {
        Token keyword = previous();
        consume(SEMICOLON, "Expect ';' after 'break'.");
        return new Stmt.Break(keyword);
    }

    // continueStatement = "continue" ";"
    private Stmt continueStatement() {
        Token keyword = previous();
        consume(SEMICOLON, "Expect ';' after 'continue'.");
        return new Stmt.Continue(keyword);
    }

    // forStatement -> "for" "(" (varDeclaration | expressionStatement | ";")
    //                     expression? ";" expression? ")" statement
    //
    // This is de-sugaring the "for" into a while loop.
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }
        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
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

    // assignment -> ( call "." )? identifier "=" assignment | ternary
    private Expr assignment() {
        Expr expr = ternary();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    // ternary -> or ( "?" ( expression ) ":" ternary )?
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

    // or -> and ("or" and)*
    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    // and -> equality ("and" equality)*
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

    // unary -> ( ( "!" | "-" ) unary | call )
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    // call -> primary ( "(" arguments? ")" | "." identifier )*
    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }

        return expr;
    }

    // arguments -> assignment ( "," assignment )*
    // Note: it needs to be assignment here and not expression because we have the comma operator.
    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() > 255) {
                    error(peek(), "Can't have more then 255 arguments.");
                }
                arguments.add(assignment());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    // primary -> ( "false" | "true" | "nil" | "this" | number | string |
    //              "super" "." identifier | "(" expression ")" | lambda | identifier )
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);
        if (match(THIS)) return new Expr.This(previous());

        if (match(SUPER)) {
            Token keyword = previous();
            consume(DOT, "Expect '.' after 'super'.");
            Token method = consume(IDENTIFIER, "Expect superclass method name.");
            return new Expr.Super(keyword, method);
        }

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression");
            return new Expr.Grouping(expr);
        }

        if (match(FUN)) {
            return lambda();
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        throw error(peek(), "Expect expression.");
    }

    private Expr.Lambda lambda() {
        consume(LEFT_PAREN, "Expect '(' after 'fun'");
        List<Token> parameters = parameters();
        consume(RIGHT_PAREN, "Expect ')' after parameters");

        consume(LEFT_BRACE, "Expect '{' before function body.");
        List<Stmt> body = block();

        return new Expr.Lambda(parameters, body);
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

    private boolean checkNext(TokenType type) {
        if (isAtEnd()) return false;
        return tokens.get(current + 1).type == type;
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
