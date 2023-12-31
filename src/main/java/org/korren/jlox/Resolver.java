package org.korren.jlox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, VarInfo>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private boolean inLoop = false;
    private ClassType currentClass = ClassType.NONE;

    private enum FunctionType {
        NONE,
        CLASSMETHOD,
        FUNCTION,
        INITIALIZER,
        LAMBDA,
        METHOD
    }
    private enum ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    private static class VarInfo {
        boolean defined = false;
        Token nameTok;
        String varType;
        boolean wasUsed = false;

        VarInfo(Token nameTok, String varType) {
            this.nameTok = nameTok;
            this.varType = varType;
        }

        VarInfo(Token nameTok, String varType, boolean defined, boolean wasUsed) {
            this(nameTok, varType);
            this.defined = defined;
            this.wasUsed = wasUsed;
        }
    }

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }


    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLambdaExpr(Expr.Lambda expr) {
        resolveLambda(expr);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'super' outside of a class.");
        } else if (currentClass != ClassType.SUBCLASS) {
            Lox.error(expr.keyword, "Can't use 'super' in a class with no superclass.");
        }

        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitTernaryExpr(Expr.Ternary expr) {
        resolve(expr.condition);
        resolve(expr.trueBranch);
        resolve(expr.falseBranch);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class.");
        }

        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (scopes.isEmpty()) return null;

        VarInfo vi = resolveLocal(expr, expr.name);
        if (vi == null) return null;

        if (vi.defined == Boolean.FALSE) {
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }
        vi.wasUsed = true;
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        if (!inLoop) Lox.error(stmt.keyword, "'break' cannot appear outside of a loop");

        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass;
        try {
            currentClass = ClassType.CLASS;

            declare(stmt.name, "class");
            define(stmt.name);

            if (stmt.superclass != null) {
                if (stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
                    Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
                }
                currentClass = ClassType.SUBCLASS;
                resolve(stmt.superclass);

                beginScope();
                scopes.peek().put("super", new VarInfo(stmt.name, "super", true, true));
            }

            beginScope();
            scopes.peek().put("this", new VarInfo(stmt.name, "this", true, true));

            for (Stmt.Function method : stmt.methods) {
                FunctionType declaration = FunctionType.METHOD;
                if (method.name.lexeme.equals("init")) {
                    declaration = FunctionType.INITIALIZER;
                }
                resolveFunction(method, declaration);
            }
            for (Stmt.Function method : stmt.classMethods) {
                if (method.name.lexeme.equals("init")) {
                    // Side-step `init` special behaviour - though might become handy if
                    // we make a way to access class fields
                    Lox.error(method.name, "Cannot have an 'init' class method");
                }
                resolveFunction(method, FunctionType.CLASSMETHOD);
            }

            endScope();

            if (stmt.superclass != null) endScope();

        } finally {
            currentClass = enclosingClass;
        }
        return null;
    }

    @Override
    public Void visitContinueStmt(Stmt.Continue stmt) {
        if (!inLoop) Lox.error(stmt.keyword, "'continue' cannot appear outside of a loop");

        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name, "function");
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword, "Can't return a value from an initializer.");
            }
            resolve(stmt.value);
        }

        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name, "variable");
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        boolean enclosingBlockInLoop = inLoop;
        try {
            inLoop = true;
            resolve(stmt.body);
        } finally {
            inLoop = enclosingBlockInLoop;
        }
        return null;
    }

    private VarInfo resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return scopes.get(i).get(name.lexeme);
            }
        }
        return null;
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        resolveFunction(function.params, function.body, type);
    }

    private void resolveLambda(Expr.Lambda expr) {
        resolveFunction(expr.params, expr.body, FunctionType.LAMBDA);
    }

    private void resolveFunction(List<Token> params, List<Stmt> body, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        try {
            currentFunction = type;
            beginScope();
            for (Token param : params) {
                declare(param, "parameter");
                define(param);
            }
            resolve(body);
            endScope();
        } finally {
            currentFunction = enclosingFunction;
        }
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void declare(Token name, String varType) {
        if (scopes.isEmpty()) return;

        Map<String, VarInfo> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already have a " + scope.get(name.lexeme).varType + " with this name in this scope.");
        }
        scope.put(name.lexeme, new VarInfo(name, varType));
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        scopes.peek().get(name.lexeme).defined = true;
    }

    private void endScope() {
        Map<String, VarInfo> scope = scopes.pop();
        scope.forEach((n, vi) -> {
            if(!vi.wasUsed) {
                Lox.error(vi.nameTok, vi.varType + " was defined but never used.");
            }
        });
    }

    private void beginScope() {
        scopes.push(new HashMap<>());
    }
}
