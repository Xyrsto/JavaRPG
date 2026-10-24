package dev.javarpg.runner.process;

/** Wall-clock arithmetic for run durations. */
public final class Elapsed {

    private Elapsed() {
    }

    /** Milliseconds since a System.nanoTime() reading. */
    public static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
