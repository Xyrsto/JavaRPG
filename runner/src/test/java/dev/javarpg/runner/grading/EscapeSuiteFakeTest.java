package dev.javarpg.runner.grading;

import static dev.javarpg.runner.support.TestQuests.stdoutQuest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.runner.fake.FakeSandboxExecutor;
import dev.javarpg.runner.guard.SourceGuard;
import dev.javarpg.runner.support.EscapeFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class EscapeSuiteFakeTest {

    private static final String RUN_ID = "11111111-2222-3333-4444-555555555555";
    private static final Quest QUEST = stdoutQuest(
            List.of(new TestCase("runs", null, "x", MatchMode.EXACT_TRIMMED)), List.of());

    @Test
    void guardDecisionMatchesEveryFixture() {
        for (String name : EscapeFixtures.NAMES) {
            boolean allowed = SourceGuard.check(EscapeFixtures.source(name), Profile.BASIC).allowed();
            assertEquals("ALLOW".equals(EscapeFixtures.guard(name)), allowed, name);
        }
    }

    @Test
    void rejectedFixturesNeverReachTheSandbox() {
        for (String name : EscapeFixtures.NAMES) {
            if (!"REJECT".equals(EscapeFixtures.guard(name))) {
                continue;
            }
            FakeSandboxExecutor fake = new FakeSandboxExecutor();
            GradeReport r = new GradingPipeline(fake, new DefaultGrader())
                    .evaluate(QUEST, TestScope.ALL, EscapeFixtures.source(name), RUN_ID);
            assertEquals(0, fake.requests().size(), name);
            assertFalse(r.passed(), name);
            assertFalse(r.summary().isBlank(), name);
            assertFalse(r.friendlyHints().isEmpty(), name);
        }
    }

    @Test
    void allowedFixturesAreGradedAsContainedFailures() {
        for (String name : EscapeFixtures.NAMES) {
            if (!"ALLOW".equals(EscapeFixtures.guard(name))) {
                continue;
            }
            SandboxStatus status = EscapeFixtures.containerStatuses(name).get(0);
            FakeSandboxExecutor fake = new FakeSandboxExecutor();
            fake.setResult(resultFor(status));
            GradeReport r = new GradingPipeline(fake, new DefaultGrader())
                    .evaluate(QUEST, TestScope.ALL, EscapeFixtures.source(name), RUN_ID);
            assertFalse(r.passed(), name);
            assertEquals(1, fake.requests().size(), name);
            assertEquals(expectedSummary(status), r.summary(), name);
        }
    }

    private static SandboxResult resultFor(SandboxStatus status) {
        switch (status) {
            case TIMEOUT:
                return FakeSandboxExecutor.timeout();
            case OUTPUT_LIMIT:
                return FakeSandboxExecutor.outputLimit();
            case MEMORY_LIMIT:
                return FakeSandboxExecutor.memoryLimit();
            case RUNTIME_ERROR:
                return FakeSandboxExecutor.runtimeError("boom");
            default:
                throw new IllegalArgumentException("No fake result for " + status);
        }
    }

    private static String expectedSummary(SandboxStatus status) {
        switch (status) {
            case TIMEOUT:
                return "Your code ran for too long and was stopped. Look for a loop that never ends.";
            case OUTPUT_LIMIT:
                return "Your code printed far too much text and was stopped.";
            case MEMORY_LIMIT:
                return "Your code used too much memory and was stopped.";
            case RUNTIME_ERROR:
                return "Your code crashed while running. The error is shown below.";
            default:
                throw new IllegalArgumentException("No expected summary for " + status);
        }
    }
}
