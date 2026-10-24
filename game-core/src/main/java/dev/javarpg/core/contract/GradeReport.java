package dev.javarpg.core.contract;

import java.util.List;

/**
 * passed        : true only if the run was OK and every test in scope passed.
 * status        : sandbox status (SANDBOX_ERROR, COMPILE_ERROR, TIMEOUT... pass through).
 * compilerOutput/stdout/stderr : verbatim, stdout/stderr already cut to 4000 chars by the Grader.
 * friendlyHints : plain-language explanations shown next to (never instead of) the raw output.
 * summary       : one friendly sentence.
 */
public record GradeReport(
        boolean passed,
        SandboxStatus status,
        String compilerOutput,
        String stdout,
        String stderr,
        List<TestOutcome> tests,
        List<String> friendlyHints,
        String summary,
        long durationMillis) { }
