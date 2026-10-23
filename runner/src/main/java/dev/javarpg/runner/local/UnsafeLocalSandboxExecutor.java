package dev.javarpg.runner.local;

import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.runner.docker.ExitCodeInterpreter;
import dev.javarpg.runner.process.Elapsed;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UNSAFE: no isolation, development only. Player code runs as a child JVM on this machine with
 * the same rights as the server process. Enabled only by javarpg.sandbox.mode=unsafe-local.
 */
public final class UnsafeLocalSandboxExecutor implements SandboxExecutor {

    private static final Logger LOG = Logger.getLogger(UnsafeLocalSandboxExecutor.class.getName());
    private static final long RUN_TIMEOUT_MILLIS = 3_000L;
    private static final String NOT_AVAILABLE = "The code runner is not available right now.";

    private final ProcessRunner processes;
    private final CompileStep compileStep;

    public UnsafeLocalSandboxExecutor() {
        this(new LocalProcessRunner());
    }

    UnsafeLocalSandboxExecutor(ProcessRunner processes) {
        this.processes = processes;
        this.compileStep = new CompileStep(processes);
    }

    @Override
    public SandboxResult execute(SandboxRequest request) {
        long start = System.nanoTime();
        try {
            if (request.profile() != Profile.BASIC) {
                return error(start, "The unsafe local runner supports BASIC challenges only.");
            }
            try (LocalWorkspace workspace = LocalWorkspace.create(request)) {
                Optional<SandboxResult> compileFailure = compileStep.compile(request, workspace, start);
                if (compileFailure.isPresent()) {
                    return compileFailure.get();
                }
                return runStep(request, workspace, start);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Unsafe local run failed", e);
            return error(start, NOT_AVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Unsafe local run interrupted", e);
            return error(start, NOT_AVAILABLE);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Unsafe local run error", e);
            return error(start, NOT_AVAILABLE);
        }
    }

    private SandboxResult runStep(SandboxRequest request, LocalWorkspace workspace, long start)
            throws IOException, InterruptedException {
        ProcessRunner.Captured ran = processes.run(JavaTools.java(workspace.classes(), request.mainClass()),
                workspace.dir(), RUN_TIMEOUT_MILLIS, false);
        SandboxStatus status = ExitCodeInterpreter.interpret(ran.exit(), ran.millis(), RUN_TIMEOUT_MILLIS,
                ran.stderr(), ran.truncated(), ran.timedOut());
        return new SandboxResult(status, ran.exit(), "", ran.stdout(), ran.stderr(),
                ran.truncated(), Elapsed.millisSince(start));
    }

    private static SandboxResult error(long start, String message) {
        return new SandboxResult(SandboxStatus.SANDBOX_ERROR, -1, "", "", message, false,
                Elapsed.millisSince(start));
    }
}
