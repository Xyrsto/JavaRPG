package dev.javarpg.server.config;

import dev.javarpg.runner.docker.DockerCleanup;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Security review F-05: removes sandbox containers older than 30 s (a crashed server leaves them behind). */
final class SandboxContainerReaper {

    private static final Logger LOG = Logger.getLogger(SandboxContainerReaper.class.getName());
    private static final long MAX_AGE_SECONDS = 30L;
    private static final long PERIOD_SECONDS = 60L;

    private SandboxContainerReaper() {
    }

    /** Starts a daemon thread that sweeps stale runner containers once a minute. */
    static void start(String dockerCommand) {
        ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "sandbox-reaper");
            thread.setDaemon(true);
            return thread;
        });
        reaper.scheduleWithFixedDelay(() -> sweep(dockerCommand), PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    private static void sweep(String dockerCommand) {
        try {
            int removed = DockerCleanup.removeStale(dockerCommand, MAX_AGE_SECONDS);
            if (removed > 0) {
                LOG.info("Sandbox reaper removed " + removed + " stale runner containers");
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Sandbox reaper failed", e);
        }
    }
}
