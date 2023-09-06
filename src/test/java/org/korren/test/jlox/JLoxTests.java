package org.korren.test.jlox;

import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class JLoxTests {
    @TestFactory
    DynamicNode fetchSampleFiles() throws URISyntaxException, IOException {
        var uri = Objects.requireNonNull(getClass().getResource("samples")).toURI();
        FileSystem fs;
        try {
            fs = FileSystems.getFileSystem(uri);
        } catch (java.lang.IllegalArgumentException e) {
            var rootUri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),"/",null, null);
            fs = FileSystems.getFileSystem(rootUri);
        }
        var pth = fs.getPath(uri.getPath());
        return pathToTest(pth);
    }

    private DynamicNode pathToTest(Path pth) {
        if (Files.isDirectory(pth)) {
            try (var files = Files.list(pth)) {
                var subTests = files
                        .filter((p) -> Files.isDirectory(p) || p.toString().endsWith("lox"))
                        .map(this::pathToTest).toArray(DynamicNode[]::new);
                return dynamicContainer(pth.getFileName().toString(), pth.toUri(), Arrays.stream(subTests));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return dynamicTest(pth.getFileName().toString(), pth.toUri(), () -> {
                var code = Files.readString(pth);

                var exp = ScriptOutput.readExpected(code);
                var out = ScriptOutput.capture(code);

                assertEquals(exp, out);
            });
        }
    }
}
