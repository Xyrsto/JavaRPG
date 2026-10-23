package dev.javarpg.runner.grading;

import static dev.javarpg.runner.support.TestQuests.methodQuest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.runner.harness.HarnessProtocol;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutputScrubberTest {

    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private final OutputScrubber scrubber = new OutputScrubber();

    @Test
    void protocolAndNonceLinesAreDroppedForHarnessRuns() {
        String raw = NONCE + "\n@@JR-" + NONCE + "|0|5\nhello";
        assertEquals("hello", scrubber.scrubStdout(raw, true));
    }

    @Test
    void nonceLikeLineIsKeptWhenNotAHarnessRun() {
        assertEquals(NONCE, scrubber.scrubStdout(NONCE, false));
    }

    @Test
    void compilerOutputThatQuotesAHiddenCallIsReplaced() {
        Quest q = methodQuest(List.of(), List.of(new TestCase("h", "secret(41)", "42", MatchMode.EXACT_TRIMMED)));
        assertEquals(HarnessProtocol.HARNESS_COMPILE_MESSAGE, scrubber.scrubCompilerOutput(q, "error near secret(41)"));
        assertEquals("plain error", scrubber.scrubCompilerOutput(q, "plain error"));
    }
}
