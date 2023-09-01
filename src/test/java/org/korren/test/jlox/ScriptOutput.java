package org.korren.test.jlox;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.korren.jlox.TokenType.LINE_COMMENT;

public class ScriptOutput {
    static final Pattern expectCmt = Pattern.compile("^ ?(?:Expect|Prints) \"([^\"]*)\"$");

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
}
