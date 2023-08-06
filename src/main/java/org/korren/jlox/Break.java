package org.korren.jlox;

public class Break extends RuntimeException {
    Break() {
        super(null, null, false, false);
    }
}
