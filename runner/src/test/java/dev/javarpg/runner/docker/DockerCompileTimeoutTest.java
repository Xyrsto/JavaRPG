package dev.javarpg.runner.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.runner.harness.HarnessProtocol;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Proves the in-container javac timeout with the real entrypoint. Current javac versions finish every
 * pathological source we tried in about a second, so no fixture can hang them; instead a stand-in javac
 * that sleeps is placed first on PATH, which exercises the same "timeout -k 1 10 javac" line and its
 * exit-102 branch.
 */
@EnabledIfSystemProperty(named = "docker.tests", matches = "true")
class DockerCompileTimeoutTest {

    @Test
    void aJavacThatNeverReturnsEndsAsExit102WithTheFixedMessage() throws Exception {
        Path input = Files.createTempDirectory("jr-ct-in");
        Path shim = Files.createTempDirectory("jr-ct-shim");
        Files.writeString(input.resolve("Main.java"), "public class Main { public static void main(String[] a) { } }");
        Files.writeString(shim.resolve("javac"), "#!/bin/sh\nexec sleep 60\n", StandardCharsets.UTF_8);

        List<String> command = List.of("docker", "run", "--rm", "--network", "none", "--read-only", "--cap-drop", "ALL",
                "--user", "65534:65534", "--tmpfs", "/work:rw,noexec,nosuid,size=32m,mode=1777",
                "--mount", "type=bind,src=" + input.toAbsolutePath() + ",dst=/in,readonly",
                "--mount", "type=bind,src=" + shim.toAbsolutePath() + ",dst=/shim,readonly",
                "-e", "PATH=/shim:/opt/java/openjdk/bin:/usr/local/bin:/usr/bin:/bin", "-e", "JR_MAIN=Main", "-e", "JR_RUN_TIMEOUT=5",
                "--entrypoint", "sh", "javarpg-runner:1", "-c", "exec /opt/jr/entrypoint.sh");
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getOutputStream().close();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(HarnessProtocol.COMPILE_TIMEOUT_MESSAGE, out);
        assertEquals(102, process.exitValue());
        assertTrue(elapsedMillis >= 10_000L && elapsedMillis < 30_000L, "took " + elapsedMillis + " ms");
    }
}
