package org.korren.test.jlox;

import org.korren.jlox.Lox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.korren.jlox.TokenType.LINE_COMMENT;

public record ScriptOutput(String stdOut, String stdErr) {
    private static final Pattern expectCmt = Pattern.compile("^ ?(?:Expect|Prints) \"([^\"]*)\"$");
    private static final Pattern expectErrCmt = Pattern.compile("^ ?Error \"([^\"]*)\"$");

    static String readExpectedOut(String script) {
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

    static ScriptOutput readExpected(String script) {
        return new ScriptOutput(ScriptOutput.readExpectedOut(script), ScriptOutput.readExpectedErrors(script));
    }

    static ScriptOutput capture(String script) throws IOException {
        try (
            var outBa = new ByteArrayOutputStream();
            var out = new PrintStream(outBa);
            var errBa = new ByteArrayOutputStream();
            var err = new PrintStream(errBa)
        ) {
            Lox.run(script, out, err);
            return new ScriptOutput(outBa.toString(), errBa.toString());
        }
    }
}
