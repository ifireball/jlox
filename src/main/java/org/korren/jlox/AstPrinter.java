package org.korren.jlox;

class AstPrinter implements Expr.Visitor<String> {
    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return parenthesize("=", expr.name, expr.value);
    }

    @Override
    public String visitTernaryExpr(Expr.Ternary expr) {
        return parenthesize("?:", expr.condition, expr.trueBranch, expr.falseBranch);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        return parenthesize("call", expr.callee, expr.arguments);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return expr.name.lexeme;
    }

    private String parenthesize(String name, Object... exprs) {
        StringBuilder builder = new StringBuilder();

        builder.append("(").append(name);
        for (Object expr : exprs) {
            builder.append(" ");
            if (expr instanceof Expr) {
                builder.append(((Expr) expr).accept(this));
            } else if (expr instanceof Token) {
                builder.append(((Token) expr).lexeme);
            } else {
                builder.append(expr.toString());
            }
        }
        builder.append(")");

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

        System.out.println(new AstPrinter().print(expression1));
        System.out.println(new AstPrinter().print(expression2));
    }
}
