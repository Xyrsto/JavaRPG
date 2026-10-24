package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.runner.harness.HarnessProtocol;

/**
 * Removes harness protocol details from text that goes into a report. Protocol lines and the nonce never
 * leave the grader (F-10): they would reveal hidden test counts or call texts.
 */
public final class OutputScrubber {

    /** Drops protocol lines (and, for harness runs, the nonce line) from text that goes into a report. */
    String scrubStdout(String raw, boolean harness) {
        if (raw.isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            String bare = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (bare.contains(HarnessProtocol.RESULT_PREFIX)
                    || (harness && HarnessProtocol.NONCE.matcher(bare.trim()).matches())) {
                continue;
            }
            if (sb.length() > 0 || !line.isEmpty()) {
                sb.append(line).append('\n');
            }
        }
        return sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";
    }

    /** Belt and braces for F-10: javac output that mentions the harness or a call text is replaced. */
    String scrubCompilerOutput(Quest quest, String output) {
        if (output.isEmpty()) {
            return output;
        }
        boolean leaks = output.contains("JrHarness") || output.contains(HarnessProtocol.RESULT_PREFIX);
        if (!leaks) {
            // Checks every call text, hidden ones included, whatever scope this run used.
            for (TestCase t : TestSelection.callTestsInScope(quest, TestScope.ALL)) {
                if (output.contains(t.call())) {
                    leaks = true;
                    break;
                }
            }
        }
        return leaks ? HarnessProtocol.HARNESS_COMPILE_MESSAGE : output;
    }
}
