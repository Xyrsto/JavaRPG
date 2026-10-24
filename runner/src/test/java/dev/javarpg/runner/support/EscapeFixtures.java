package dev.javarpg.runner.support;

import dev.javarpg.core.contract.SandboxStatus;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class EscapeFixtures {

    public static final List<String> NAMES = List.of(
        "infinite-loop", "fork-bomb", "memory-bomb", "giant-stdout", "system-exit", "stdin-read",
        "file-write", "file-read", "network-call", "reflection", "runtime-exec", "class-loading",
        "stack-overflow", "static-init-abuse",
        "static-import-exit", "reflection-getmethods", "network-reflection");

    /** Fixtures that are a METHOD_RETURN Solution (run through the harness), not a Main program. */
    public static final List<String> HARNESS_NAMES = List.of("harness-forgery", "compile-leak", "compile-hang");

    private static final Properties EXPECTATIONS = loadExpectations();

    private EscapeFixtures() {
    }

    public static String source(String name) {
        String path = "/escape/" + name + ".java";
        try (InputStream in = EscapeFixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing escape fixture " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String guard(String name) {
        return EXPECTATIONS.getProperty(name + ".guard");
    }

    public static List<SandboxStatus> containerStatuses(String name) {
        List<SandboxStatus> list = new ArrayList<>();
        for (String part : EXPECTATIONS.getProperty(name + ".container").split(",")) {
            list.add(SandboxStatus.valueOf(part.trim()));
        }
        return list;
    }

    private static Properties loadExpectations() {
        Properties props = new Properties();
        try (InputStream in = EscapeFixtures.class.getResourceAsStream("/escape/expectations.properties")) {
            if (in == null) {
                throw new IllegalStateException("Missing escape/expectations.properties");
            }
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return props;
    }
}
