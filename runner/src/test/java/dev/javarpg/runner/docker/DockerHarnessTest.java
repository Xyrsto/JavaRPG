package dev.javarpg.runner.docker;

import static dev.javarpg.runner.support.TestQuests.methodQuest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.runner.grading.DefaultGrader;
import dev.javarpg.runner.harness.HarnessGenerator;
import dev.javarpg.runner.harness.HarnessProtocol;
import dev.javarpg.runner.support.EscapeFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Harness protocol, compile-output leak and javac timeout checks inside the real container. */
@EnabledIfSystemProperty(named = "docker.tests", matches = "true")
class DockerHarnessTest {

    private static final Quest QUEST = methodQuest(
            List.of(new TestCase("add", "Solution.add(2, 3)", "5", MatchMode.EXACT_TRIMMED)),
            List.of(new TestCase("big", "Solution.add(2147483647, 1)", "-2147483648", MatchMode.EXACT_TRIMMED)));
    private static final List<String> CALLS = List.of("Solution.add(2, 3)", "Solution.add(2147483647, 1)");

    private final DockerSandboxExecutor executor = new DockerSandboxExecutor("docker", "javarpg-runner:1");

    private SandboxResult run(String solution) {
        return executor.execute(new SandboxRequest(UUID.randomUUID().toString(), Profile.BASIC,
                Map.of("Solution.java", solution, "JrHarness.java", HarnessGenerator.generate(CALLS)), "JrHarness"));
    }

    @Test
    void correctSolutionPassesThroughTheNonceProtocol() {
        SandboxResult r = run("public class Solution { public static int add(int a, int b) { return a + b; } }");
        assertEquals(SandboxStatus.OK, r.status());
        GradeReport report = new DefaultGrader().grade(QUEST, TestScope.ALL, r, "x");
        assertTrue(report.passed());
        assertFalse(report.stdout().contains("@@JR"));
    }

    @Test
    void forgedLinesThenExitGradeAsFailed() {
        String source = EscapeFixtures.source("harness-forgery");
        SandboxResult r = run(source);
        GradeReport report = new DefaultGrader().grade(QUEST, TestScope.ALL, r, source);
        assertFalse(report.passed());
        assertFalse(report.stdout().contains("@@JR"));
    }

    @Test
    void emptySolutionClassDoesNotLeakHarnessCalls() {
        SandboxResult r = run(EscapeFixtures.source("compile-leak"));
        assertEquals(SandboxStatus.COMPILE_ERROR, r.status());
        assertEquals(HarnessProtocol.HARNESS_COMPILE_MESSAGE, r.compilerOutput().trim());
        assertFalse(r.compilerOutput().contains("Solution."));
        assertFalse(r.compilerOutput().contains("2147483647"));
    }

    @Test
    void pathologicalSourceIsBoundedByTheJavacTimeout() {
        SandboxResult r = run(EscapeFixtures.source("compile-hang"));
        assertTrue(r.durationMillis() < 20_000L);
        assertTrue(EscapeFixtures.containerStatuses("compile-hang").contains(r.status()), "got " + r.status());
        if (r.status() == SandboxStatus.TIMEOUT) {
            assertEquals("", r.compilerOutput());
            assertEquals(HarnessProtocol.COMPILE_TIMEOUT_MESSAGE, r.stderr());
        }
    }

    @Test
    void sandboxContainersCarryTheLabelAndNoneAreLeftBehind() throws Exception {
        run("class Solution {}");
        Process process = new ProcessBuilder("docker", "ps", "-aq", "--filter",
                "label=" + DockerCommandBuilder.SANDBOX_LABEL).redirectErrorStream(true).start();
        process.getOutputStream().close();
        String out = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        process.waitFor();
        assertEquals("", out);
    }
}
