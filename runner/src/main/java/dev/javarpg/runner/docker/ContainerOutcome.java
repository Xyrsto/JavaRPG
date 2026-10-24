package dev.javarpg.runner.docker;

/** What the docker CLI process produced for one run, before it is mapped to a SandboxResult. */
record ContainerOutcome(int exit, String stdout, String stderr, boolean limitHit, boolean watchdogFired,
                        long durationMillis) {
}
