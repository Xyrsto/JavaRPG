package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.contract.TestScope;

/** The one-line, player-facing summary of a run. */
public final class SummaryMessages {

    String summary(SandboxStatus status, TestScope scope, boolean passed, int passedCount, int total) {
        if (status != SandboxStatus.OK) {
            return statusSummary(status);
        }
        if (passed) {
            return scope == TestScope.ALL
                    ? "Challenge complete!"
                    : "All visible tests pass. Press Submit to finish the challenge.";
        }
        return "Almost! " + passedCount + " of " + total + " tests passed.";
    }

    private static String statusSummary(SandboxStatus status) {
        return switch (status) {
            case COMPILE_ERROR -> "Your code did not compile yet. Read the message below, then try again.";
            case TIMEOUT -> "Your code ran for too long and was stopped. Look for a loop that never ends.";
            case OUTPUT_LIMIT -> "Your code printed far too much text and was stopped.";
            case MEMORY_LIMIT -> "Your code used too much memory and was stopped.";
            case RUNTIME_ERROR -> "Your code crashed while running. The error is shown below.";
            case SANDBOX_ERROR -> "The code runner is not available right now. Nothing was lost; try again in a moment.";
            case OK -> "Challenge complete!";
        };
    }
}
