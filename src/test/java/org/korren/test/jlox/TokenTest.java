package org.korren.test.jlox;

import org.junit.jupiter.api.Test;
import org.korren.jlox.Token;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.korren.jlox.TokenType.LINE_COMMENT;

class TokenTest {

    @Test
    void testEquals() {
        var a = new Token(LINE_COMMENT, null, "foo", 1);
        var b = new Token(LINE_COMMENT, null, "foo", 1);
        var la = List.of(a);
        var lb = List.of(b);

        assertEquals(a, b);
        assertIterableEquals(la, lb);
    }
}
