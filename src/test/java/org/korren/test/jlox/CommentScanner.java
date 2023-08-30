package org.korren.test.jlox;

import org.korren.jlox.Token;
import org.korren.jlox.TokenType;

import java.util.ArrayList;
import java.util.List;

import static org.korren.jlox.TokenType.*;

public class CommentScanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int startLine = 1;
    private int line = 1;

    CommentScanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while(!isAtEnd()) {
            start = current;
            startLine = line;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '/' -> {
                if (match('/')) {
                    lineComment();
                } else if (match('*')) {
                    blockComment();
                }
            }
            case '\n' -> line++;
            case '"' -> string();
            default -> {
                // Ignore everything else
            }
        }
    }

    private void lineComment() {
        while (peek() != '\n' && !isAtEnd()) advance();
        String value = source.substring(start + 2, current);
        addToken(LINE_COMMENT, value);
    }

    private void blockComment() {
        while (peek() != '*' || peekNext() != '/') {
            if (isAtEnd()) {
                // We just ignore unterminated block comments and treat them as if they were terminated by the EOF
                break;
            }
            if (peek() == '\n') line++;
            advance();
        }

        String value = source.substring(start + 2, current);
        addToken(BLOCK_COMMENT, value);

        if (!isAtEnd()) {
            // Eat the terminating '*/'
            advance();
            advance();
        }
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        if (isAtEnd()) {
            return;
        }

        // The closing '"'.
        advance();
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    private void addToken(TokenType type, Object literal) {
        tokens.add(new Token(type, null, literal, startLine));
    }

    private char advance() {
        return source.charAt(current++);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }
}
