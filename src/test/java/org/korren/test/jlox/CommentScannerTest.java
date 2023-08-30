package org.korren.test.jlox;

import org.junit.jupiter.api.Test;
import org.korren.jlox.Token;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.korren.jlox.TokenType.*;

class CommentScannerTest {

    @Test
    void scanTokens() {
        var code = """
        /* a block comment
        with more then 1 line */
        fun foo(a, b) {
            // line comment 1
            return a + b; // line comment 2
        }
        
        foo(17, 23);//line comment 3
        """;
        var expected = Arrays.asList(
                new Token(BLOCK_COMMENT, null, " a block comment\nwith more then 1 line ", 1),
                new Token(LINE_COMMENT, null, " line comment 1", 4),
                new Token(LINE_COMMENT, null, " line comment 2", 5),
                new Token(LINE_COMMENT, null, "line comment 3", 8),
                new Token(EOF, "", null, 9)
        );

        var cs = new CommentScanner(code);
        var out = cs.scanTokens();

        assertIterableEquals(expected, out);
    }
}
