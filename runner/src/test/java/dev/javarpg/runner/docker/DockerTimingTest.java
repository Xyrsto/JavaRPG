package dev.javarpg.runner.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "docker.tests", matches = "true")
class DockerTimingTest {

    private static final String HELLO = "public class Main {\n"
            + "    public static void main(String[] args) {\n"
            + "        System.out.println(\"Hello, Adventurer!\");\n"
            + "    }\n"
            + "}\n";

    private static SandboxRequest hello() {
        return new SandboxRequest(UUID.randomUUID().toString(), Profile.BASIC, Map.of("Main.java", HELLO), "Main");
    }

    @Test
    void p95OfTwentySequentialRunsIsUnderThreeSeconds() {
        DockerSandboxExecutor executor = new DockerSandboxExecutor("docker", "javarpg-runner:1");
        executor.execute(hello());
        long[] millis = new long[20];
        for (int i = 0; i < millis.length; i++) {
            long start = System.nanoTime();
            SandboxResult r = executor.execute(hello());
            millis[i] = (System.nanoTime() - start) / 1_000_000L;
            assertEquals(SandboxStatus.OK, r.status());
        }
        Arrays.sort(millis);
        long p95 = millis[18];
        assertTrue(p95 < 3000L, "p95 was " + p95 + " ms");
    }
}
