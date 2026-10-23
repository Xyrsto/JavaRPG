package dev.javarpg.runner.docker;

import dev.javarpg.core.contract.SandboxStatus;

/**
 * Maps the container exit code and host-side facts to a SandboxStatus. Note that a player's own
 * exit code can look like one of these values; the entrypoint remaps run-phase 100 to 101 for that reason.
 */
public final class ExitCodeInterpreter {

    static final int COMPILE_FAILED = 100;
    /** A javac step inside the container hit its timeout (entrypoint.sh). */
    public static final int COMPILE_TIMED_OUT = 102;
    private static final int TIMEOUT_EXIT = 124;
    private static final int KILLED_EXIT = 137;
    private static final long KILL_SLACK_MILLIS = 200L;

    private ExitCodeInterpreter() {
    }

    public static SandboxStatus interpret(int exitCode, long durationMillis, long timeoutMillis,
                                          String stderr, boolean stdoutLimitHit, boolean watchdogFired) {
        if (watchdogFired) {
            return SandboxStatus.TIMEOUT;
        }
        if (stdoutLimitHit) {
            return SandboxStatus.OUTPUT_LIMIT;
        }
        if (exitCode == COMPILE_TIMED_OUT) {
            return SandboxStatus.TIMEOUT;
        }
        if (exitCode == COMPILE_FAILED) {
            return SandboxStatus.COMPILE_ERROR;
        }
        if (exitCode == 0) {
            return SandboxStatus.OK;
        }
        if (exitCode == TIMEOUT_EXIT) {
            return SandboxStatus.TIMEOUT;
        }
        if (exitCode == KILLED_EXIT && durationMillis >= timeoutMillis - KILL_SLACK_MILLIS) {
            return SandboxStatus.TIMEOUT;
        }
        if (exitCode == KILLED_EXIT || (stderr != null && stderr.contains("OutOfMemoryError"))) {
            return SandboxStatus.MEMORY_LIMIT;
        }
        return SandboxStatus.RUNTIME_ERROR;
    }
}
