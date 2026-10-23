package dev.javarpg.runner.local;

import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.runner.harness.HarnessProtocol;
import dev.javarpg.runner.process.Elapsed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compiles a run's sources. Same two-step compile as docker/entrypoint.sh (F-10): the player's files alone,
 * then the harness, so a player file can never see or replace the harness.
 */
final class CompileStep {

    static final long COMPILE_TIMEOUT_MILLIS = 15_000L;

    private final ProcessRunner processes;

    CompileStep(ProcessRunner processes) {
        this.processes = processes;
    }

    /** Empty when everything compiled; otherwise the result to return to the caller. */
    Optional<SandboxResult> compile(SandboxRequest request, LocalWorkspace workspace, long startNanos)
            throws IOException, InterruptedException {
        List<String> playerFiles = new ArrayList<>();
        boolean hasHarness = false;
        for (String name : request.sources().keySet()) {
            if (HarnessProtocol.HARNESS_FILE.equals(name)) {
                hasHarness = true;
            } else {
                playerFiles.add(name);
            }
        }
        ProcessRunner.Captured built = processes.run(JavaTools.javac(workspace.classes(), null, playerFiles),
                workspace.dir(), COMPILE_TIMEOUT_MILLIS, true);
        if (built.timedOut()) {
            return Optional.of(compileTimeout(built, startNanos));
        }
        if (built.exit() != 0) {
            return Optional.of(new SandboxResult(SandboxStatus.COMPILE_ERROR, built.exit(), built.stdout(), "", "",
                    built.truncated(), Elapsed.millisSince(startNanos)));
        }
        if (hasHarness) {
            ProcessRunner.Captured harness = processes.run(
                    JavaTools.javac(workspace.classes(), workspace.classes(), List.of(HarnessProtocol.HARNESS_FILE)),
                    workspace.dir(), COMPILE_TIMEOUT_MILLIS, true);
            if (harness.timedOut()) {
                return Optional.of(compileTimeout(harness, startNanos));
            }
            if (harness.exit() != 0) {
                // The real javac output may quote hidden call texts, so the player only sees a fixed message.
                return Optional.of(new SandboxResult(SandboxStatus.COMPILE_ERROR, harness.exit(),
                        HarnessProtocol.HARNESS_COMPILE_MESSAGE, "", "", false, Elapsed.millisSince(startNanos)));
            }
        }
        return Optional.empty();
    }

    private static SandboxResult compileTimeout(ProcessRunner.Captured step, long startNanos) {
        return new SandboxResult(SandboxStatus.TIMEOUT, step.exit(), "", "",
                HarnessProtocol.COMPILE_TIMEOUT_MESSAGE, step.truncated(), Elapsed.millisSince(startNanos));
    }
}
