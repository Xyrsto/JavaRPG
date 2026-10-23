package dev.javarpg.runner.docker;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.runner.support.EscapeFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "docker.tests", matches = "true")
class DockerEscapeTest {

    @Test
    void everyFixtureIsContainedByTheContainer() throws IOException, InterruptedException {
        DockerSandboxExecutor executor = new DockerSandboxExecutor("docker", "javarpg-runner:1");
        Path canary = Files.createTempFile("jr-canary-", ".txt");
        Files.writeString(canary, "original", StandardCharsets.UTF_8);
        try {
            for (String name : EscapeFixtures.NAMES) {
                SandboxRequest request = new SandboxRequest(UUID.randomUUID().toString(), Profile.BASIC,
                        Map.of("Main.java", EscapeFixtures.source(name)), "Main");
                SandboxResult result = executor.execute(request);
                assertTrue(EscapeFixtures.containerStatuses(name).contains(result.status()),
                        name + " gave " + result.status());
                assertTrue(result.durationMillis() < 20_000L, name);
                assertFalse(result.stdout().contains("uid="), name + " must not run commands");
                assertEquals("original", Files.readString(canary, StandardCharsets.UTF_8), name);
            }
            assertEquals("", listSandboxContainers());
        } finally {
            Files.deleteIfExists(canary);
        }
    }

    private static String listSandboxContainers() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("docker", "ps", "-aq", "--filter", "label=" + DockerCommandBuilder.SANDBOX_LABEL)
                .redirectErrorStream(true).start();
        process.getOutputStream().close();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        process.waitFor(30, SECONDS);
        return out;
    }
}
