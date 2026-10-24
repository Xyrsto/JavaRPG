package dev.javarpg.server.exception;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** FR-003: no error code is written as a string literal in server code; every place takes it from ErrorCode. */
class NoRawErrorCodeStringsTest {

    @Test
    void noErrorCodeNameAppearsAsALiteralInServerMainCode() throws Exception {
        StringBuilder names = new StringBuilder();
        for (ErrorCode code : ErrorCode.values()) {
            names.append(names.isEmpty() ? "" : "|").append(code.name());
        }
        Pattern literal = Pattern.compile("\"(" + names + ")\"");
        Path here = Path.of("").toAbsolutePath();
        Path root = "server".equals(String.valueOf(here.getFileName())) ? here : here.resolve("server");
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root.resolve("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = literal.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    found.add(root.relativize(file) + ": " + matcher.group());
                }
            }
        }
        assertTrue(found.isEmpty(), "error codes written as strings: " + found);
    }
}
