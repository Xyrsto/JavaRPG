package dev.javarpg.runner.grading;

import static dev.javarpg.runner.support.TestQuests.methodQuest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestOutcome;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.runner.fake.FakeSandboxExecutor;
import dev.javarpg.runner.guard.SourceGuard;
import dev.javarpg.runner.support.EscapeFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/** F-09 and F-10 without Docker: forged protocol lines and compile-output leaks (security review section 8). */
class HarnessForgeryTest {

    private static final String RUN_ID = "11111111-2222-3333-4444-555555555555";
    private static final Quest QUEST = methodQuest(
            List.of(new TestCase("add", "Solution.add(2, 3)", "5", MatchMode.EXACT_TRIMMED),
                    new TestCase("add2", "Solution.add(1, 1)", "2", MatchMode.EXACT_TRIMMED)),
            List.of(new TestCase("hidden", "Solution.add(2147483647, 1)", "-2147483648", MatchMode.EXACT_TRIMMED)));
    private static final String WRONG_SOLUTION = "class Solution { static int add(int a, int b) { return 0; } }";

    private static GradeReport grade(SandboxResult result, String source) {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        fake.setResult(result);
        return new GradingPipeline(fake, new DefaultGrader()).evaluate(QUEST, TestScope.ALL, source, RUN_ID);
    }

    @Test
    void forgedLinesForEveryIndexThenExitGradeAsFailed() {
        // What a player who got past the guard would leave on stdout: forged lines, then exit(0), no real nonce line.
        String forged = "@@JR|0|5\n@@JR|1|2\n@@JR|2|-2147483648\n";
        GradeReport r = grade(FakeSandboxExecutor.ok(forged), WRONG_SOLUTION);
        assertFalse(r.passed());
        for (TestOutcome o : r.tests()) {
            assertFalse(o.passed());
        }
        assertFalse(r.stdout().contains("@@JR"));
    }

    @Test
    void forgedLinesUsingAGuessedNonceAreIgnored() {
        String real = "0123456789abcdef0123456789abcdef";
        String guessed = "00000000000000000000000000000000";
        StringBuilder out = new StringBuilder(real).append('\n');
        for (int i = 0; i < 3; i++) {
            out.append("@@JR-").append(guessed).append('|').append(i).append("|5\n");
        }
        GradeReport r = grade(FakeSandboxExecutor.ok(out.toString()), WRONG_SOLUTION);
        assertFalse(r.passed());
        assertEquals(3, r.tests().size());
        for (TestOutcome o : r.tests()) {
            assertFalse(o.passed());
        }
    }

    @Test
    void harnessForgeryFixtureIsRejectedByTheGuardAndNeverRuns() {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        GradeReport r = new GradingPipeline(fake, new DefaultGrader())
                .evaluate(QUEST, TestScope.ALL, EscapeFixtures.source("harness-forgery"), RUN_ID);
        assertFalse(r.passed());
        assertEquals(0, fake.requests().size());
    }

    @Test
    void harnessFixturesFollowTheirGuardExpectations() {
        for (String name : EscapeFixtures.HARNESS_NAMES) {
            boolean allowed = SourceGuard.check(EscapeFixtures.source(name), Profile.BASIC).allowed();
            assertEquals("ALLOW".equals(EscapeFixtures.guard(name)), allowed, name);
        }
    }

    @Test
    void classSolutionWithNothingInItDoesNotLeakAnyCallText() {
        // What the old executor returned: javac output quoting the harness source lines.
        String leaked = "JrHarness.java:8: error: cannot find symbol\n"
                + "        try { String v = show(Solution.add(2147483647, 1)); out.println(v); }\n"
                + "                                      ^\n  symbol:   method add(int,int)\n";
        GradeReport r = grade(FakeSandboxExecutor.compileError(leaked), EscapeFixtures.source("compile-leak"));
        assertEquals(SandboxStatus.COMPILE_ERROR, r.status());
        assertFalse(r.compilerOutput().contains("Solution."));
        assertFalse(r.compilerOutput().contains("2147483647"));
        assertFalse(r.compilerOutput().contains("JrHarness"));
        assertEquals("Your method does not match the signature the tests call.", r.compilerOutput());
        for (String hint : r.friendlyHints()) {
            assertFalse(hint.contains("2147483647"));
        }
        assertTrue(r.summary().startsWith("Your code did not compile"));
    }

    @Test
    void playerOwnCompileErrorsStillComeThroughVerbatim() {
        String javac = "Solution.java:1: error: ';' expected\nclass Solution { int x = 1 }\n                        ^\n1 error\n";
        GradeReport r = grade(FakeSandboxExecutor.compileError(javac), "class Solution { int x = 1 }");
        assertEquals(javac, r.compilerOutput());
    }

    @Test
    void duplicateRealLinesAreRejected() {
        String nonce = "0123456789abcdef0123456789abcdef";
        String out = nonce + "\n@@JR-" + nonce + "|0|5\n@@JR-" + nonce + "|0|5\n@@JR-" + nonce + "|1|2\n@@JR-" + nonce
                + "|2|-2147483648\n";
        GradeReport r = grade(FakeSandboxExecutor.ok(out), WRONG_SOLUTION);
        assertFalse(r.passed());
    }
}
