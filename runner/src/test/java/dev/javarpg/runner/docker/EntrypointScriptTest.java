package dev.javarpg.runner.docker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.runner.harness.HarnessProtocol;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Static checks on docker/entrypoint.sh (no Docker needed): timeouts, two-step compile, shared messages. */
class EntrypointScriptTest {

    private static String script() throws IOException {
        Path path = Path.of("docker", "entrypoint.sh");
        Assumptions.assumeTrue(Files.exists(path), "run from the runner module directory");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void everyJavacRunsUnderTimeout() throws IOException {
        String s = script();
        int javacCalls = 0;
        for (String line : s.split("\n")) {
            if (!line.trim().startsWith("#") && line.contains(" javac ")) {
                javacCalls++;
                assertTrue(line.contains("timeout -k 1 10 javac"), line);
            }
        }
        assertTrue(javacCalls == 2, "player compile and harness compile");
        assertTrue(s.contains("exit 102"));
        assertTrue(s.contains("124") && s.contains("137"));
    }

    @Test
    void harnessIsCompiledSeparatelyWithFixedMessages() throws IOException {
        String s = script();
        assertTrue(s.contains("HARNESS_MSG=\"" + HarnessProtocol.HARNESS_COMPILE_MESSAGE + "\""));
        assertTrue(s.contains("TIMEOUT_MSG=\"" + HarnessProtocol.COMPILE_TIMEOUT_MESSAGE + "\""));
        assertTrue(s.contains("-cp /work/out -d /work/out JrHarness.java"));
        assertTrue(s.contains("$players"));
        assertTrue(s.contains("-eq 102 ]; then rc=101"), "run-phase 102 must be remapped");
    }
}
