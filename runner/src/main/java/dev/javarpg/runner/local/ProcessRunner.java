package dev.javarpg.runner.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Runs one child process to completion with a time limit and captured, size-capped output. */
interface ProcessRunner {

    /** What a finished (or killed) child process left behind. */
    record Captured(int exit, String stdout, String stderr, boolean truncated, boolean timedOut, long millis) {
    }

    /** mergeErrors folds stderr into stdout (javac prints diagnostics on stderr). */
    Captured run(List<String> command, Path cwd, long timeoutMillis, boolean mergeErrors)
            throws IOException, InterruptedException;
}
