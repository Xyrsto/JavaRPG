package dev.javarpg.runner.local;

import dev.javarpg.runner.process.Elapsed;
import dev.javarpg.runner.process.StreamCapture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** {@link ProcessRunner} that starts real child processes on this machine and kills them on timeout. */
final class LocalProcessRunner implements ProcessRunner {

    private static final int STDOUT_CAP = 64 * 1024;
    private static final int STDERR_CAP = 16 * 1024;

    @Override
    public Captured run(List<String> command, Path cwd, long timeoutMillis, boolean mergeErrors)
            throws IOException, InterruptedException {
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(mergeErrors)
                .start();
        process.getOutputStream().close();
        StreamCapture out = new StreamCapture("jr-local-reader", process.getInputStream(), STDOUT_CAP, null);
        StreamCapture err = mergeErrors ? null
                : new StreamCapture("jr-local-reader", process.getErrorStream(), STDERR_CAP, null);
        out.start();
        if (err != null) {
            err.start();
        }
        boolean timedOut = !process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        if (timedOut) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        out.join(2000);
        if (err != null) {
            err.join(2000);
        }
        int exit = process.isAlive() ? -1 : process.exitValue();
        return new Captured(exit, out.text(), err == null ? "" : err.text(), out.limitHit(), timedOut,
                Elapsed.millisSince(start));
    }
}
