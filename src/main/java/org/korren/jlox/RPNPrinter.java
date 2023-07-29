package org.korren.jlox;

public class RPNPrinter implements Expr.Visitor<String> {
    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return push("=", expr.value, expr.name);
    }

    @Override
    public String visitTernaryExpr(Expr.Ternary expr) {
        return push("?:", expr.condition, expr.trueBranch, expr.falseBranch);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return push(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return expr.expression.accept(this);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return push(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return expr.name.lexeme;
    }

    private String push(String name, Object... exprs) {
        StringBuilder builder = new StringBuilder();

        for (Object expr : exprs) {
            if (expr instanceof Expr) {
                builder.append(((Expr) expr).accept(this));
            } else if (expr instanceof Token) {
                builder.append(((Token) expr).lexeme);
            } else {
                builder.append(expr.toString());
            }
            builder.append(" ");
        }
        builder.append(name);

        return builder.toString();
    }

    public static void main(String[] args) {
        Expr expression1 = new Expr.Binary(
                new Expr.Unary(
                        new Token(TokenType.MINUS, "-", null, 1),
                        new Expr.Literal(123)
                ),
                new Token(TokenType.STAR, "*", null, 1),
                new Expr.Grouping(
                        new Expr.Literal(45.67)
                )
        );
        Expr expression2 = new Expr.Binary(
                new Expr.Grouping(
                        new Expr.Binary(
                                new Expr.Literal(1),
                                new Token(TokenType.PLUS, "+", null, 1),
                                new Expr.Literal(2)
                        )
                ),
                new Token(TokenType.STAR, "*", null, 1),
                new Expr.Grouping(
                        new Expr.Binary(
                                new Expr.Literal(4),
                                new Token(TokenType.MINUS, "-", null, 1),
                                new Expr.Literal(3)
                        )
                )
        );

        System.out.println(new RPNPrinter().print(expression1));
        System.out.println(new RPNPrinter().print(expression2));
    }
}
