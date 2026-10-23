package dev.javarpg.runner.process;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Disposal of per-run directories. */
public final class Directories {

    private static final Logger LOG = Logger.getLogger(Directories.class.getName());

    private Directories() {
    }

    /** Deletes a directory tree. Failures are logged; they never mask the run's result. */
    public static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(Directories::deleteQuietly);
        } catch (IOException | UncheckedIOException e) {
            LOG.log(Level.WARNING, "Could not clean up sandbox directory", e);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not delete sandbox file", e);
        }
    }
}
