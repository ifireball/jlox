package org.korren.test.jlox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptOutputTest {

    @Test
    void readExpected() {
        var code = """
                /* a block comment
                with more then 1 line */
                fun foo(a, b) {
                    // line comment 1
                    print a + b; // line comment 2
                }
                
                foo(17, 23); // Prints "40"
                print "bye"; // Expect "bye"
                """;
        var expected = """
                40
                bye
                """;
        var out = ScriptOutput.readExpected(code);

        assertEquals(expected, out);
    }
}
