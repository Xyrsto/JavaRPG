package dev.javarpg.runner.docker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.io.UncheckedIOException;

/**
 * Removes leftover sandbox containers and temp directories. Containers are found by the label
 * javarpg.sandbox=1 (never by an unanchored name filter, F-06) and double-checked to be named "jr-*".
 */
public final class DockerCleanup {

    private static final Logger LOG = Logger.getLogger(DockerCleanup.class.getName());
    private static final String LABEL_FILTER = "label=" + DockerCommandBuilder.SANDBOX_LABEL;

    private DockerCleanup() {
    }

    /** Removes every labelled sandbox container (for example after a server restart). Returns the count removed. */
    public static int removeOrphans(String dockerBinary) {
        return removeOlderThan(dockerBinary, 0L);
    }

    /**
     * Reaper (F-05): removes labelled sandbox containers created more than minAgeSeconds ago. A run lasts
     * at most about 15 s, so 30 is a safe age. Meant to be called periodically from server code.
     */
    public static int removeStale(String dockerBinary, long minAgeSeconds) {
        return removeOlderThan(dockerBinary, Math.max(0L, minAgeSeconds));
    }

    private static int removeOlderThan(String dockerBinary, long minAgeSeconds) {
        try {
            String listing = capture(List.of(dockerBinary, "ps", "-aq", "--filter", LABEL_FILTER));
            List<String> ids = new ArrayList<>();
            for (String line : listing.split("\n")) {
                String id = line.trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
            if (ids.isEmpty()) {
                return 0;
            }
            List<String> inspect = new ArrayList<>(List.of(dockerBinary, "inspect", "--format",
                    "{{.Id}} {{.Name}} {{.Created}}"));
            inspect.addAll(ids);
            List<String> doomed = selectRemovable(capture(inspect), Instant.now(), minAgeSeconds);
            if (doomed.isEmpty()) {
                return 0;
            }
            List<String> command = new ArrayList<>(List.of(dockerBinary, "rm", "-f"));
            command.addAll(doomed);
            if (runQuiet(command)) {
                LOG.info("Removed " + doomed.size() + " leftover sandbox container(s).");
                return doomed.size();
            }
            LOG.warning("Could not remove leftover sandbox containers: " + doomed);
            return 0;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not list leftover sandbox containers", e);
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    /**
     * Parses lines of "id name created" (docker inspect format) and returns the ids whose name starts with
     * "/jr-" and that are at least minAgeSeconds old. Unparseable lines are skipped, never removed.
     */
    static List<String> selectRemovable(String inspectOutput, Instant now, long minAgeSeconds) {
        List<String> ids = new ArrayList<>();
        for (String line : inspectOutput.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 3 || !parts[1].startsWith("/jr-")) {
                continue;
            }
            try {
                Instant created = Instant.parse(parts[2]);
                if (created.plusSeconds(minAgeSeconds).compareTo(now) <= 0) {
                    ids.add(parts[0]);
                }
            } catch (DateTimeParseException e) {
                LOG.log(Level.FINE, "Skipping container with unreadable creation time");
            }
        }
        return ids;
    }

    private static String capture(List<String> command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly();
        }
        return out;
    }

    static boolean runQuiet(List<String> command) {
        try {
            Process p = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            p.getOutputStream().close();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "A docker command could not run", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static void killContainer(String dockerBinary, String runId) {
        runQuiet(List.of(dockerBinary, "kill", "jr-" + runId));
    }

    static void removeContainer(String dockerBinary, String runId) {
        runQuiet(List.of(dockerBinary, "rm", "-f", "jr-" + runId));
    }

    /** Deletes a directory tree. Failures are logged; they never mask the run's result. */
    public static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(DockerCleanup::deleteQuietly);
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
