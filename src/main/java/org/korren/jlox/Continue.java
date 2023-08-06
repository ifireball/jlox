package org.korren.jlox;

public class Continue extends RuntimeException {
    Continue() {
        super(null, null, false, false);
    }
}
