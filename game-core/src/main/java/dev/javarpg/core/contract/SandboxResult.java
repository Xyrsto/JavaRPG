package dev.javarpg.core.contract;

/**
 * compilerOutput : javac output verbatim ("" when compile succeeded).
 * stdout         : captured stdout, at most 64 KiB, UTF-8.   stderr: captured stderr, at most 16 KiB.
 * exitCode       : process exit code, -1 when unknown.
 */
public record SandboxResult(
        SandboxStatus status,
        int exitCode,
        String compilerOutput,
        String stdout,
        String stderr,
        boolean stdoutTruncated,
        long durationMillis) { }
