package org.korren.jlox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
    final String name;
    private final Map<String, LoxFunction> methods;

    static private final LoxClass metaClassClass = new LoxClass();

    private LoxClass(LoxClass klass, String name, Map<String, LoxFunction> methods) {
        super(klass);
        this.name = name;
        this.methods = methods;
    }

    // For MetaClassClass
    private LoxClass() {
        this(null, "MetaClassClass", new HashMap<>());
    }

    // For meta classes
    private LoxClass(String name, Map<String, LoxFunction> methods) {
        this(metaClassClass, name + "MetaClass", methods);
    }

    // For normal classes
    LoxClass(String name, Map<String, LoxFunction> methods, Map<String, LoxFunction> classMethods) {
        this(new LoxClass(name, classMethods), name, methods);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }
        return instance;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    public LoxFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }

        return null;
    }
}
