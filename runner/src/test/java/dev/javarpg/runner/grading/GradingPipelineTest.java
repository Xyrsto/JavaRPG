package dev.javarpg.runner.grading;

import static dev.javarpg.runner.support.TestQuests.methodQuest;
import static dev.javarpg.runner.support.TestQuests.stdoutQuest;
import static dev.javarpg.runner.support.TestQuests.withProfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.runner.fake.FakeSandboxExecutor;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GradingPipelineTest {

    private static final String RUN_ID = "11111111-2222-3333-4444-555555555555";
    private static final String HELLO = "public class Main { public static void main(String[] a) { System.out.println(\"Hello\"); } }";
    private static final String ADD = "public class Solution { public static int add(int a, int b) { return a + b; } }";

    @Test
    void stdoutQuestSendsMainJavaWithMainClassMain() {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        fake.setResult(FakeSandboxExecutor.ok("Hello"));
        Quest q = stdoutQuest(List.of(new TestCase("hello", null, "Hello", MatchMode.EXACT_TRIMMED)), List.of());
        GradeReport r = new GradingPipeline(fake, new DefaultGrader()).evaluate(q, TestScope.VISIBLE, HELLO, RUN_ID);
        SandboxRequest req = fake.lastRequest();
        assertEquals("Main", req.mainClass());
        assertEquals(Set.of("Main.java"), req.sources().keySet());
        assertEquals(RUN_ID, req.runId());
        assertTrue(r.passed());
    }

    @Test
    void methodQuestSendsSolutionAndHarnessWithOnlyInScopeCalls() {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        fake.setResult(FakeSandboxExecutor.ok("@@JR|0|5\n"));
        Quest q = methodQuest(
                List.of(new TestCase("add", "Solution.add(2, 3)", "5", MatchMode.EXACT_TRIMMED)),
                List.of(new TestCase("hidden", "Solution.add(4, 4)", "8", MatchMode.EXACT_TRIMMED)));
        new GradingPipeline(fake, new DefaultGrader()).evaluate(q, TestScope.VISIBLE, ADD, RUN_ID);
        SandboxRequest req = fake.lastRequest();
        assertEquals("JrHarness", req.mainClass());
        assertEquals(Set.of("Solution.java", "JrHarness.java"), req.sources().keySet());
        String harness = req.sources().get("JrHarness.java");
        assertTrue(harness.contains("show(Solution.add(2, 3))"));
        assertFalse(harness.contains("Solution.add(4, 4)"));
    }

    @Test
    void allScopeHarnessIncludesHiddenCalls() {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        Quest q = methodQuest(
                List.of(new TestCase("add", "Solution.add(2, 3)", "5", MatchMode.EXACT_TRIMMED)),
                List.of(new TestCase("hidden", "Solution.add(4, 4)", "8", MatchMode.EXACT_TRIMMED)));
        new GradingPipeline(fake, new DefaultGrader()).evaluate(q, TestScope.ALL, ADD, RUN_ID);
        assertTrue(fake.lastRequest().sources().get("JrHarness.java").contains("show(Solution.add(4, 4))"));
    }

    @Test
    void guardRejectionReturnsFailedReportAndNeverCallsExecutor() {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        Quest q = stdoutQuest(List.of(new TestCase("x", null, "x", MatchMode.EXACT_TRIMMED)), List.of());
        String bad = "public class Main { public static void main(String[] a) { System.exit(0); } }";
        GradeReport r = new GradingPipeline(fake, new DefaultGrader()).evaluate(q, TestScope.ALL, bad, RUN_ID);
        assertFalse(r.passed());
        assertEquals(0, fake.requests().size());
        assertEquals("That code is not allowed in this quest.", r.summary());
        assertFalse(r.friendlyHints().isEmpty());
    }

    @Test
    void nonBasicProfileIsRejectedWithQuestProfileUnsupported() {
        Quest q = withProfile(stdoutQuest(List.of(), List.of()), Profile.TESTING);
        GameException ex = assertThrows(GameException.class,
                () -> new GradingPipeline(new FakeSandboxExecutor(), new DefaultGrader()).evaluate(q, TestScope.ALL, HELLO, RUN_ID));
        assertEquals(ErrorCode.QUEST_PROFILE_UNSUPPORTED, ex.code());
    }

    @Test
    void sandboxErrorPassesThroughWithSummary() {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        fake.setResult(FakeSandboxExecutor.sandboxError("down"));
        Quest q = stdoutQuest(List.of(new TestCase("x", null, "x", MatchMode.EXACT_TRIMMED)), List.of());
        GradeReport r = new GradingPipeline(fake, new DefaultGrader()).evaluate(q, TestScope.VISIBLE, HELLO, RUN_ID);
        assertFalse(r.passed());
        assertEquals("The code runner is not available right now. Nothing was lost; try again in a moment.", r.summary());
    }

    @Test
    void passesOnlyWhenAllInScopeTestsPass() {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        fake.setResult(FakeSandboxExecutor.ok("A"));
        Quest q = stdoutQuest(
                List.of(new TestCase("a", null, "A", MatchMode.EXACT_TRIMMED)),
                List.of(new TestCase("b", null, "B", MatchMode.CONTAINS)));
        GradingPipeline pipeline = new GradingPipeline(fake, new DefaultGrader());
        assertTrue(pipeline.evaluate(q, TestScope.VISIBLE, HELLO, RUN_ID).passed());
        assertFalse(pipeline.evaluate(q, TestScope.ALL, HELLO, RUN_ID).passed());
    }
}
