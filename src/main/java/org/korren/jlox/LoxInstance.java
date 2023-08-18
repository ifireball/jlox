package org.korren.jlox;

import java.util.HashMap;
import java.util.Map;

class LoxInstance {
    private final LoxClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    LoxInstance(LoxClass klass) {
        if (klass == null && this instanceof LoxClass) {
            // Special case for the MetaClassClass
            this.klass = (LoxClass) this;
            return;
        }
        this.klass = klass;
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }

    Object get(Token name) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }

        LoxFunction method = klass.findMethod(name.lexeme);
        if (method != null) return method.bind(this);

        throw new RuntimeError(name, "Undefined property '" + name.lexeme + "'.");
    }

    void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }
}
