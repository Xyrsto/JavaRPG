package dev.javarpg.runner.grading;

import java.util.Map;

/** What a finished run produced, as the test kinds need to see it. */
record RunContext(String stdoutNorm, Map<Integer, HarnessResult> results, String playerSource) {
}
