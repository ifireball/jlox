package org.korren.test.jlox;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ScriptOutputTest {
    static final String code = """
                /* a block comment
                with more then 1 line */
                fun foo(a, b) {
                    // line comment 1
                    print a + b; // line comment 2
                }
                
                foo(17, 23); // Prints "40"
                print "bye"; // Expect "bye"
                """;
    static final String expected = """
                40
                bye
                """;

    @Test
    void readExpected() {
        var out = ScriptOutput.readExpected(code);

        assertEquals(expected, out);
    }

    @Test
    void capture() throws IOException {
        var out = ScriptOutput.capture(code);

        assertEquals(expected, out);
    }
}
