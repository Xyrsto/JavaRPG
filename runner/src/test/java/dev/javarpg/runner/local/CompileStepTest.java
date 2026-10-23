package dev.javarpg.runner.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.runner.harness.HarnessProtocol;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class CompileStepTest {

    /** Hands out canned results in order and remembers the commands it was asked to run. */
    private static final class ScriptedRunner implements ProcessRunner {
        final Queue<Captured> answers = new ArrayDeque<>();
        final List<List<String>> commands = new ArrayList<>();

        @Override
        public Captured run(List<String> command, Path cwd, long timeoutMillis, boolean mergeErrors) {
            commands.add(command);
            return answers.remove();
        }
    }

    private static ProcessRunner.Captured captured(int exit, String out, boolean timedOut) {
        return new ProcessRunner.Captured(exit, out, "", false, timedOut, 1L);
    }

    private static SandboxRequest request(boolean withHarness) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Main.java", "class Main { }");
        if (withHarness) {
            sources.put(HarnessProtocol.HARNESS_FILE, "class JrHarness { }");
        }
        return new SandboxRequest("run", Profile.BASIC, sources, "Main");
    }

    @Test
    void successfulCompileOfBothStepsReturnsEmpty() throws IOException, InterruptedException {
        ScriptedRunner runner = new ScriptedRunner();
        runner.answers.add(captured(0, "", false));
        runner.answers.add(captured(0, "", false));
        try (LocalWorkspace ws = LocalWorkspace.create(request(true))) {
            assertTrue(new CompileStep(runner).compile(request(true), ws, System.nanoTime()).isEmpty());
        }
        assertEquals(2, runner.commands.size());
        assertTrue(runner.commands.get(0).contains("Main.java"));
        assertTrue(runner.commands.get(1).contains(HarnessProtocol.HARNESS_FILE));
    }

    @Test
    void playerCompileErrorIsReturnedVerbatim() throws IOException, InterruptedException {
        ScriptedRunner runner = new ScriptedRunner();
        runner.answers.add(captured(1, "Main.java:1: error", false));
        try (LocalWorkspace ws = LocalWorkspace.create(request(true))) {
            Optional<SandboxResult> r = new CompileStep(runner).compile(request(true), ws, System.nanoTime());
            assertEquals(SandboxStatus.COMPILE_ERROR, r.orElseThrow().status());
            assertEquals("Main.java:1: error", r.get().compilerOutput());
        }
        assertEquals(1, runner.commands.size());
    }

    @Test
    void harnessCompileErrorShowsOnlyTheFixedMessage() throws IOException, InterruptedException {
        ScriptedRunner runner = new ScriptedRunner();
        runner.answers.add(captured(0, "", false));
        runner.answers.add(captured(1, "JrHarness.java: secret(41)", false));
        try (LocalWorkspace ws = LocalWorkspace.create(request(true))) {
            SandboxResult r = new CompileStep(runner).compile(request(true), ws, System.nanoTime()).orElseThrow();
            assertEquals(HarnessProtocol.HARNESS_COMPILE_MESSAGE, r.compilerOutput());
        }
    }

    @Test
    void compileTimeoutMapsToTimeoutWithTheCompileMessage() throws IOException, InterruptedException {
        ScriptedRunner runner = new ScriptedRunner();
        runner.answers.add(captured(-1, "", true));
        try (LocalWorkspace ws = LocalWorkspace.create(request(false))) {
            SandboxResult r = new CompileStep(runner).compile(request(false), ws, System.nanoTime()).orElseThrow();
            assertEquals(SandboxStatus.TIMEOUT, r.status());
            assertEquals(HarnessProtocol.COMPILE_TIMEOUT_MESSAGE, r.stderr());
        }
    }
}
