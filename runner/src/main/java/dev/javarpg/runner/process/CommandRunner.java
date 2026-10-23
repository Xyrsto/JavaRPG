package dev.javarpg.runner.process;

import java.io.IOException;
import java.util.List;

/** Runs short host commands (docker ps, rm, kill). A seam so the cleanup logic can be tested without Docker. */
public interface CommandRunner {

    /** Runs the command and returns its stdout. Stderr is discarded. */
    String capture(List<String> command) throws IOException, InterruptedException;

    /** Runs the command with all output discarded. True only when it exits 0 within its time limit. */
    boolean runQuietly(List<String> command);
}
