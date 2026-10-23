package dev.javarpg.core.contract;

/**
 * OK: exit code 0, no limit hit.            COMPILE_ERROR: javac failed (compilerOutput filled).
 * TIMEOUT: wall-clock limit hit.            OUTPUT_LIMIT: stdout exceeded the cap, run killed.
 * MEMORY_LIMIT: OOM (heap or container).    RUNTIME_ERROR: non-zero exit (uncaught exception, stack overflow, thread bomb...).
 * SANDBOX_ERROR: infrastructure failure (docker missing, image missing); never the player's fault.
 */
public enum SandboxStatus { OK, COMPILE_ERROR, TIMEOUT, OUTPUT_LIMIT, MEMORY_LIMIT, RUNTIME_ERROR, SANDBOX_ERROR }
