package org.korren.test.jlox;

import org.korren.jlox.Lox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.korren.jlox.TokenType.LINE_COMMENT;

public class ScriptOutput {
    static final Pattern expectCmt = Pattern.compile("^ ?(?:Expect|Prints) \"([^\"]*)\"$");
    static final Pattern expectErrCmt = Pattern.compile("^ ?Error \"([^\"]*)\"$");

    static String readExpected(String script) {
        var cs = new CommentScanner(script);
        return cs.scanTokens().stream()
                .filter((t) -> t.type == LINE_COMMENT)
                .map((t) -> (String)t.literal)
                .map(expectCmt::matcher)
                .filter((Matcher::matches))
                .map((m) -> m.group(1) + "\n")
                .collect(Collectors.joining());
    }

    static String readExpectedErrors(String script) {
        var cs = new CommentScanner(script);
        return cs.scanTokens().stream()
                .filter((t) -> t.type == LINE_COMMENT)
                .map((t) -> (String)t.literal)
                .map(expectErrCmt::matcher)
                .filter((Matcher::matches))
                .map((m) -> m.group(1) + "\n")
                .collect(Collectors.joining());
    }

    static String capture(String script) throws IOException {
        try (
            var ba = new ByteArrayOutputStream();
            var ps = new PrintStream(ba)
        ) {
            Lox.run(script, ps);
            return ba.toString();
        }
    }
}
