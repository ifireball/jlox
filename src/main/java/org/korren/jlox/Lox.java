package org.korren.jlox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    private static Interpreter interpreter = new Interpreter();
    static boolean hadError = false;
    static boolean hadRuntimeError = false;
    static PrintStream stdErr = System.err;

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        // Indicate an error in the exit code
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for(;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            replRun(line);
            hadError = false;
            hadRuntimeError = false;
        }
    }

    private static void replRun(String source) {
        List<Stmt> statements = getReplStatements(source);

        // Stop is there was a syntax error
        if (hadError) return;

        // TODO: create special REPL mode that uses these:
        // System.out.println(new AstPrinter().print(expression));
        // System.out.println(new RPNPrinter().print(expression));

        if (!statements.isEmpty()) {
            Stmt last = statements.get(statements.size() - 1);
            // If the last statement is an expression, convert it into a print
            if (last instanceof Stmt.Expression) {
                statements.set(statements.size() - 1, new Stmt.Print(((Stmt.Expression) last).expression));
            }
        }

        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);

        // Stop if there was a resolution error.
        if (hadError) return;

        interpreter.interpret(statements);
    }

    private static List<Stmt> getReplStatements(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        // auto add ";" at the end if its missing
        if (tokens.size() >= 2) {
            Token last = tokens.get(tokens.size() - 2);
            if (last.type != TokenType.SEMICOLON && last.type != TokenType.RIGHT_BRACE) {
                Token semi = new Token(TokenType.SEMICOLON, ";", null, last.line);
                tokens.add(tokens.size() - 1, semi);
            }
        }
        Parser parser = new Parser(tokens);
        return parser.parse();
    }


    public static void run(String source, PrintStream stdOut, PrintStream stdErr) {
        var currentErr = Lox.stdErr;
        Lox.stdErr = stdErr;
        try {
            Scanner scanner = new Scanner(source);
            List<Token> tokens = scanner.scanTokens();
            Parser parser = new Parser(tokens);
            List<Stmt> statements = parser.parse();

            // Stop if there was a syntax error
            if (hadError) return;

            Resolver resolver = new Resolver(interpreter);
            resolver.resolve(statements);

            // Stop if there was a resolution error.
            if (hadError) return;

            interpreter.interpret(statements, stdOut);
        } finally {
            Lox.stdErr = currentErr;
        }
    }

    private static void run(String source) {
        run(source, System.out, System.err);
    }

    public static void reset() {
        interpreter = new Interpreter();
        hadError = false;
    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    private static void report(int line, String where, String message) {
        stdErr.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

    public static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    public static void runtimeError(RuntimeError error) {
        stdErr.println(error.getMessage() + "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }
}
