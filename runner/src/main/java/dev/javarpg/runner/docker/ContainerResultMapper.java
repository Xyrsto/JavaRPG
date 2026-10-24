package dev.javarpg.runner.docker;

import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.runner.harness.HarnessProtocol;
import java.util.logging.Logger;

/** Maps a container's exit code and captured output to the SandboxResult the grader sees. */
final class ContainerResultMapper {

    private static final Logger LOG = Logger.getLogger(ContainerResultMapper.class.getName());
    private static final String NOT_AVAILABLE = "The code runner is not available right now.";

    private ContainerResultMapper() {
    }

    static SandboxResult map(ContainerOutcome o, LimitProfile limits) {
        int exit = o.exit();
        // F-03: Docker CLI/daemon failures carry host paths and versions. Log them, return a generic error.
        if (!o.watchdogFired() && isDockerFailure(exit, o.stderr())) {
            LOG.warning("docker run failed (exit " + exit + "): " + o.stderr());
            return sandboxError(o.durationMillis());
        }
        if (exit == ExitCodeInterpreter.COMPILE_TIMED_OUT && !o.watchdogFired() && !o.limitHit()) {
            return new SandboxResult(SandboxStatus.TIMEOUT, exit, "", "",
                    HarnessProtocol.COMPILE_TIMEOUT_MESSAGE, false, o.durationMillis());
        }
        SandboxStatus status = ExitCodeInterpreter.interpret(exit, o.durationMillis(),
                limits.runTimeoutSeconds() * 1000L, o.stderr(), o.limitHit(), o.watchdogFired());
        boolean compile = status == SandboxStatus.COMPILE_ERROR;
        return new SandboxResult(status, exit, compile ? o.stdout() : "", compile ? "" : o.stdout(), o.stderr(),
                o.limitHit(), o.durationMillis());
    }

    static boolean isDockerFailure(int exit, String stderr) {
        String trimmed = stderr == null ? "" : stderr.stripLeading();
        return exit == 125 || exit == 126 || exit == 127
                || trimmed.startsWith("docker:") || trimmed.startsWith("Error response from daemon");
    }

    /** Generic infrastructure failure; never reveals host details to the player. */
    static SandboxResult sandboxError(long durationMillis) {
        return new SandboxResult(SandboxStatus.SANDBOX_ERROR, -1, "", "", NOT_AVAILABLE, false, durationMillis);
    }
}
