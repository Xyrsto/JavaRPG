package dev.javarpg.runner.docker;

import dev.javarpg.runner.process.Elapsed;
import dev.javarpg.runner.process.StreamCapture;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Starts the docker CLI for one run, captures its capped output and enforces the host-side watchdog.
 * Whatever happens, the container is removed afterwards.
 */
final class ContainerRunner {

    static final long WATCHDOG_MILLIS = 15_000L;
    static final int STDOUT_CAP = 64 * 1024;
    static final int STDERR_CAP = 16 * 1024;

    private final String dockerBinary;
    private final ContainerReaper reaper;

    ContainerRunner(String dockerBinary, ContainerReaper reaper) {
        this.dockerBinary = dockerBinary;
        this.reaper = reaper;
    }

    /** startNanos is the run's System.nanoTime() start, so the reported duration covers the whole run. */
    ContainerOutcome run(String runId, List<String> args, long startNanos) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(dockerBinary);
        command.addAll(args);
        Process process = new ProcessBuilder(command).start();
        process.getOutputStream().close();
        // Too much stdout: kill the container at once instead of waiting for the watchdog.
        StreamCapture stdout = new StreamCapture("jr-sandbox-reader", process.getInputStream(), STDOUT_CAP,
                () -> reaper.kill(runId));
        StreamCapture stderr = new StreamCapture("jr-sandbox-reader", process.getErrorStream(), STDERR_CAP, null);
        stdout.start();
        stderr.start();
        try {
            boolean watchdogFired = false;
            if (!process.waitFor(WATCHDOG_MILLIS, TimeUnit.MILLISECONDS)) {
                watchdogFired = true;
                reaper.kill(runId);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            stdout.join(2000);
            stderr.join(2000);
            int exit = process.isAlive() ? -1 : process.exitValue();
            return new ContainerOutcome(exit, stdout.text(), stderr.text(), stdout.limitHit(), watchdogFired,
                    Elapsed.millisSince(startNanos));
        } finally {
            reaper.remove(runId);
        }
    }
}
