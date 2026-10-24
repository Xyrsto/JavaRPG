package dev.javarpg.runner.docker;

import dev.javarpg.runner.process.Directories;
import dev.javarpg.runner.process.SystemCommandRunner;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Public entry points for removing leftover sandbox containers and temp directories. The work is done by
 * {@link ContainerReaper} and {@link Directories}.
 */
public final class DockerCleanup {

    private DockerCleanup() {
    }

    /** Removes every labelled sandbox container (for example after a server restart). Returns the count removed. */
    public static int removeOrphans(String dockerBinary) {
        return reaper(dockerBinary).removeOlderThan(0L);
    }

    /**
     * Reaper (F-05): removes labelled sandbox containers created more than minAgeSeconds ago. A run lasts
     * at most about 15 s, so 30 is a safe age. Meant to be called periodically from server code.
     */
    public static int removeStale(String dockerBinary, long minAgeSeconds) {
        return reaper(dockerBinary).removeOlderThan(Math.max(0L, minAgeSeconds));
    }

    static List<String> selectRemovable(String inspectOutput, Instant now, long minAgeSeconds) {
        return ContainerReaper.selectRemovable(inspectOutput, now, minAgeSeconds);
    }

    /** Deletes a directory tree. Failures are logged; they never mask the run's result. */
    public static void deleteRecursively(Path root) {
        Directories.deleteRecursively(root);
    }

    private static ContainerReaper reaper(String dockerBinary) {
        return new ContainerReaper(new SystemCommandRunner(), dockerBinary);
    }
}
