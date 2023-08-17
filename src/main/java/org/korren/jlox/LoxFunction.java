package org.korren.jlox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final String name;
    private final List<Token> params;
    private final List<Stmt> body;
    private final Environment closure;
    private final boolean isInitializer;


    private LoxFunction(String name, List<Token> params, List<Stmt> body, Environment closure, boolean isInitializer) {
        this.isInitializer = isInitializer;
        this.closure = closure;
        this.name = name;
        this.params = params;
        this.body = body;
    }

    // For lambdas
    LoxFunction(Expr.Lambda expr, Environment closure) {
        this("lambda", expr.params, expr.body, closure, false);
    }

    // For methods (some of which are initializers)
    LoxFunction(Stmt.Function stmt, Environment environment, boolean isInitializer) {
        this(stmt.name.lexeme, stmt.params, stmt.body, environment, isInitializer);
    }

    // For "regular" functions
    LoxFunction(Stmt.Function stmt, Environment environment) {
        this(stmt, environment, false);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);
        for (int i = 0; i < params.size(); i++) {
            environment.define(params.get(i).lexeme, arguments.get(i));
        }

        try {
            interpreter.executeBlock(body, environment);
        } catch (Return returnValue) {
            if (isInitializer) return closure.getAt(0, "this");

            return returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, "this");
        return null;
    }

    @Override
    public int arity() {
        return params.size();
    }

    @Override
    public String toString() {
        return "<fn " + name + ">";
    }

    LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new LoxFunction(name, params, body, environment, isInitializer);
    }
}
