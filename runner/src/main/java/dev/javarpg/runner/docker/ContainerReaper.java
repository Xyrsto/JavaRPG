package dev.javarpg.runner.docker;

import dev.javarpg.runner.process.CommandRunner;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds and removes sandbox containers. Containers are found by the label javarpg.sandbox=1 (never by an
 * unanchored name filter, F-06) and double-checked to be named "jr-*".
 */
final class ContainerReaper {

    private static final Logger LOG = Logger.getLogger(ContainerReaper.class.getName());
    private static final String LABEL_FILTER = "label=" + DockerCommandBuilder.SANDBOX_LABEL;

    private final CommandRunner commands;
    private final String dockerBinary;

    ContainerReaper(CommandRunner commands, String dockerBinary) {
        this.commands = commands;
        this.dockerBinary = dockerBinary;
    }

    /** Removes labelled sandbox containers created at least minAgeSeconds ago. Returns the count removed. */
    int removeOlderThan(long minAgeSeconds) {
        try {
            String listing = commands.capture(List.of(dockerBinary, "ps", "-aq", "--filter", LABEL_FILTER));
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
            List<String> doomed = selectRemovable(commands.capture(inspect), Instant.now(), minAgeSeconds);
            if (doomed.isEmpty()) {
                return 0;
            }
            List<String> command = new ArrayList<>(List.of(dockerBinary, "rm", "-f"));
            command.addAll(doomed);
            if (commands.runQuietly(command)) {
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

    void kill(String runId) {
        commands.runQuietly(List.of(dockerBinary, "kill", "jr-" + runId));
    }

    void remove(String runId) {
        commands.runQuietly(List.of(dockerBinary, "rm", "-f", "jr-" + runId));
    }
}
