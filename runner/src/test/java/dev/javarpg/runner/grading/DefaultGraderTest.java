package dev.javarpg.runner.grading;

import static dev.javarpg.runner.support.TestQuests.methodQuest;
import static dev.javarpg.runner.support.TestQuests.quest;
import static dev.javarpg.runner.support.TestQuests.stdoutQuest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.ChallengeKind;
import dev.javarpg.core.contract.CommonMistake;
import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.MistakeSource;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestOutcome;
import dev.javarpg.core.contract.TestScope;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultGraderTest {

    private static final DefaultGrader GRADER = new DefaultGrader();
    private static final String NONCE = "0123456789abcdef0123456789abcdef";

    /** Harness stdout: the nonce line, then the given result bodies such as "0|5" or "1!EXC|msg". */
    private static String harnessOut(String... bodies) {
        StringBuilder sb = new StringBuilder(NONCE).append('\n');
        for (String body : bodies) {
            sb.append("@@JR-").append(NONCE).append('|').append(body).append('\n');
        }
        return sb.toString();
    }

    private static SandboxResult ok(String stdout) {
        return new SandboxResult(SandboxStatus.OK, 0, "", stdout, "", false, 5L);
    }

    private static SandboxResult with(SandboxStatus status, String stdout) {
        return new SandboxResult(status, 1, "", stdout, "", false, 5L);
    }

    private static TestCase exact(String name, String expected) {
        return new TestCase(name, null, expected, MatchMode.EXACT_TRIMMED);
    }

    private static TestCase call(String name, String callText, String expected) {
        return new TestCase(name, callText, expected, MatchMode.EXACT_TRIMMED);
    }

    @Test
    void exactTrimmedIgnoresLineEndingsAndTrailingSpaces() {
        Quest q = stdoutQuest(List.of(exact("prints", "Fire: 7\nWater: 2.5")), List.of());
        GradeReport r = GRADER.grade(q, TestScope.VISIBLE, ok("\r\nFire: 7  \r\nWater: 2.5\r\n\r\n"), "src");
        assertTrue(r.passed());
        assertTrue(r.tests().get(0).passed());
        assertEquals("All visible tests pass. Press Submit to finish the challenge.", r.summary());
    }

    @Test
    void wrongOutputGivesAlmostSummary() {
        Quest q = stdoutQuest(List.of(exact("a", "Fire: 7"), exact("b", "Water: 2.5")), List.of());
        GradeReport r = GRADER.grade(q, TestScope.VISIBLE, ok("Fire: 7\nWater: 9"), "src");
        assertFalse(r.passed());
        assertTrue(r.tests().get(0).passed());
        assertFalse(r.tests().get(1).passed());
        assertFalse(r.tests().get(1).message().isBlank());
        assertEquals("Almost! 1 of 2 tests passed.", r.summary());
    }

    @Test
    void containsAndNotContainsUseNormalizedStdout() {
        Quest q = stdoutQuest(List.of(
                new TestCase("has", null, "Total: 12", MatchMode.CONTAINS),
                new TestCase("no seven", null, "Total: 7", MatchMode.NOT_CONTAINS)), List.of());
        assertTrue(GRADER.grade(q, TestScope.VISIBLE, ok("Intro\nTotal: 12  \n"), "src").passed());
        GradeReport bad = GRADER.grade(q, TestScope.VISIBLE, ok("Total: 12 and Total: 7"), "src");
        assertFalse(bad.passed());
        assertFalse(bad.tests().get(1).passed());
    }

    @Test
    void sourceRegexIgnoresCommentedCode() {
        Quest q = stdoutQuest(List.of(new TestCase("uses fire", null, "\\bint\\s+fire\\s*=", MatchMode.SOURCE_REGEX)), List.of());
        assertFalse(GRADER.grade(q, TestScope.VISIBLE, ok(""), "// int fire = 1;\nint other = 2;").passed());
        assertTrue(GRADER.grade(q, TestScope.VISIBLE, ok(""), "int fire = 7;").passed());
    }

    @Test
    void visibleScopeIgnoresHiddenAndAllScopeIncludesThem() {
        Quest q = stdoutQuest(List.of(exact("shown", "A")),
                List.of(new TestCase("secret", null, "B", MatchMode.CONTAINS)));
        GradeReport visible = GRADER.grade(q, TestScope.VISIBLE, ok("A"), "src");
        assertEquals(1, visible.tests().size());
        assertTrue(visible.passed());

        GradeReport all = GRADER.grade(q, TestScope.ALL, ok("A"), "src");
        assertEquals(2, all.tests().size());
        TestOutcome hidden = all.tests().get(1);
        assertTrue(hidden.hidden());
        assertEquals("Hidden test 1", hidden.name());
        assertNull(hidden.expected());
        assertNull(hidden.actual());
        assertFalse(all.passed());
        assertEquals("Almost! 1 of 2 tests passed.", all.summary());
    }

    @Test
    void hiddenFailureRevealsNothing() {
        Quest q = stdoutQuest(List.of(), List.of(exact("secret", "SECRET-VALUE")));
        GradeReport r = GRADER.grade(q, TestScope.ALL, ok("nope"), "src");
        TestOutcome o = r.tests().get(0);
        assertEquals("Hidden test 1", o.name());
        assertNull(o.expected());
        assertNull(o.actual());
        assertFalse(o.message().contains("SECRET-VALUE"));
        assertFalse(r.passed());
    }

    @Test
    void methodReturnResultsAreMatchedByIndexAcrossScopes() {
        Quest q = methodQuest(
                List.of(call("add", "Solution.add(2, 3)", "5"), call("neg", "Solution.add(-1, 1)", "0")),
                List.of(call("hidden", "Solution.add(10, 10)", "20")));
        GradeReport r = GRADER.grade(q, TestScope.ALL, ok(harnessOut("0|5", "1|1", "2|20")), "src");
        assertTrue(r.tests().get(0).passed());
        assertFalse(r.tests().get(1).passed());
        assertTrue(r.tests().get(2).passed());
        assertEquals("Hidden test 1", r.tests().get(2).name());
        assertEquals("Almost! 2 of 3 tests passed.", r.summary());
    }

    @Test
    void duplicateIndexIsRejectedInsteadOfOverwritten() {
        Quest q = methodQuest(List.of(call("add", "Solution.add(2, 3)", "5"), call("b", "Solution.b()", "1")), List.of());
        GradeReport r = GRADER.grade(q, TestScope.VISIBLE, ok(harnessOut("0|1", "0|5", "1|1")), "src");
        assertFalse(r.passed());
        assertFalse(r.tests().get(0).passed());
        assertFalse(r.tests().get(1).passed());
    }

    @Test
    void resultLinesWithoutTheNonceAreIgnored() {
        Quest q = methodQuest(List.of(call("a", "Solution.a()", "1"), call("b", "Solution.b()", "2")), List.of());
        String wrongNonce = "ffffffffffffffffffffffffffffffff";
        String out = NONCE + "\n@@JR|0|1\n@@JR-" + wrongNonce + "|0|1\n@@JR-" + NONCE + "|1|2\n";
        GradeReport r = GRADER.grade(q, TestScope.VISIBLE, ok(out), "src");
        assertFalse(r.tests().get(0).passed());
        assertTrue(r.tests().get(0).message().contains("No result"));
        assertTrue(r.tests().get(1).passed());
    }

    @Test
    void forgedLinesWithoutAnyNonceLineFailEveryTest() {
        Quest q = methodQuest(List.of(call("a", "Solution.a()", "1")), List.of(call("h", "Solution.h()", "2")));
        GradeReport r = GRADER.grade(q, TestScope.ALL, ok("@@JR|0|1\n@@JR|1|2\n"), "src");
        assertFalse(r.passed());
        assertFalse(r.tests().get(0).passed());
        assertFalse(r.tests().get(1).passed());
    }

    @Test
    void nonceLineMustBeFirst() {
        Quest q = methodQuest(List.of(call("a", "Solution.a()", "1")), List.of());
        String out = "noise\n" + NONCE + "\n@@JR-" + NONCE + "|0|1\n";
        assertFalse(GRADER.grade(q, TestScope.VISIBLE, ok(out), "src").passed());
    }

    @Test
    void protocolLinesAndNonceNeverReachTheReportStdout() {
        Quest q = methodQuest(List.of(call("a", "Solution.a()", "1")), List.of(call("h", "Solution.h()", "2")));
        GradeReport r = GRADER.grade(q, TestScope.ALL, ok(harnessOut("0|1", "1|2")), "src");
        assertTrue(r.passed());
        assertFalse(r.stdout().contains("@@JR"));
        assertFalse(r.stdout().contains(NONCE));
        assertEquals("", r.stdout());
    }

    @Test
    void sourceRegexPatternIsNeverReturnedAsExpected() {
        TestCase visible = new TestCase("uses fire", null, "\\bint\\s+fire\\b", MatchMode.SOURCE_REGEX);
        TestCase hidden = new TestCase("hidden", null, "\\bint\\s+water\\b", MatchMode.SOURCE_REGEX);
        Quest q = stdoutQuest(List.of(visible), List.of(hidden));
        for (String source : List.of("int fire = 1; int water = 2;", "nothing")) {
            GradeReport pass = GRADER.grade(q, TestScope.ALL, ok(""), source);
            assertNull(pass.tests().get(0).expected());
            assertNull(pass.tests().get(1).expected());
        }
        GradeReport notRun = GRADER.grade(q, TestScope.ALL, with(SandboxStatus.TIMEOUT, ""), "x");
        assertNull(notRun.tests().get(0).expected());
    }

    @Test
    void harnessCompileLeakInCompilerOutputIsReplacedByFixedMessage() {
        Quest q = methodQuest(List.of(call("a", "Solution.add(2, 3)", "5")),
                List.of(call("h", "Solution.add(2147483647, 1)", "x")));
        String leaked = "JrHarness.java:9: error: cannot find symbol\n"
                + "        try { String v = show(Solution.add(2147483647, 1)); }\n                                      ^\n";
        SandboxResult res = new SandboxResult(SandboxStatus.COMPILE_ERROR, 100, leaked, "", "", false, 5L);
        GradeReport r = GRADER.grade(q, TestScope.ALL, res, "class Solution {}");
        assertEquals("Your method does not match the signature the tests call.", r.compilerOutput());
        assertFalse(r.compilerOutput().contains("Solution.add"));
    }

    @Test
    void catastrophicRegexIsBoundedAndTreatedAsNoMatch() {
        List<CommonMistake> mistakes = List.of(new CommonMistake(MistakeSource.SOURCE, "(a+)+$", "M-regex"));
        Quest q = quest(ChallengeKind.STDOUT, Profile.BASIC, List.of(exact("t", "x")), List.of(), mistakes);
        String source = "a".repeat(60) + "b";
        GradeReport[] holder = new GradeReport[1];
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(20),
                () -> {
                    holder[0] = GRADER.grade(q, TestScope.VISIBLE, ok("y"), source);
                });
        assertFalse(holder[0].friendlyHints().contains("M-regex"));
        assertFalse(holder[0].passed());
    }

    @Test
    void exceptionLineFailsTheTestWithItsMessage() {
        Quest q = methodQuest(List.of(call("div", "Solution.div(1, 0)", "1")), List.of());
        GradeReport r = GRADER.grade(q, TestScope.VISIBLE,
                ok(harnessOut("0!EXC|java.lang.ArithmeticException: / by zero")), "src");
        assertFalse(r.passed());
        assertTrue(r.tests().get(0).message().contains("threw an exception"));
        assertTrue(r.tests().get(0).message().contains("/ by zero"));
    }

    @Test
    void missingIndexFailsWithNoResultMessage() {
        Quest q = methodQuest(List.of(call("a", "Solution.a()", "1"), call("b", "Solution.b()", "2")), List.of());
        GradeReport r = GRADER.grade(q, TestScope.VISIBLE, ok(harnessOut("0|1")), "src");
        assertTrue(r.tests().get(0).passed());
        assertFalse(r.tests().get(1).passed());
        assertTrue(r.tests().get(1).message().contains("No result"));
    }

    @Test
    void escapedValuesAreDecodedBeforeComparing() {
        Quest q = methodQuest(List.of(call("text", "Solution.text()", "a\nb")), List.of());
        assertTrue(GRADER.grade(q, TestScope.VISIBLE, ok(harnessOut("0|a\\nb")), "src").passed());
    }

    @Test
    void compileErrorKeepsVerbatimOutputAndAddsHint() {
        String javac = "Main.java:3: error: ';' expected\n        int x = 1\n                 ^\n1 error\n";
        SandboxResult res = new SandboxResult(SandboxStatus.COMPILE_ERROR, 100, javac, "", "", false, 800L);
        Quest q = stdoutQuest(List.of(exact("prints", "x")), List.of());
        GradeReport r = GRADER.grade(q, TestScope.VISIBLE, res, "src");
        assertFalse(r.passed());
        assertEquals(SandboxStatus.COMPILE_ERROR, r.status());
        assertEquals(javac, r.compilerOutput());
        assertEquals("Your code did not compile yet. Read the message below, then try again.", r.summary());
        assertTrue(r.friendlyHints().get(0).startsWith("Java statements end with a semicolon"));
        assertFalse(r.tests().get(0).passed());
    }

    @Test
    void nonOkStatusNeverPassesEvenWhenOutputMatches() {
        Quest q = stdoutQuest(List.of(exact("a", "A")), List.of());
        SandboxStatus[] statuses = {
            SandboxStatus.TIMEOUT, SandboxStatus.OUTPUT_LIMIT, SandboxStatus.MEMORY_LIMIT,
            SandboxStatus.RUNTIME_ERROR, SandboxStatus.SANDBOX_ERROR};
        String[] summaries = {
            "Your code ran for too long and was stopped. Look for a loop that never ends.",
            "Your code printed far too much text and was stopped.",
            "Your code used too much memory and was stopped.",
            "Your code crashed while running. The error is shown below.",
            "The code runner is not available right now. Nothing was lost; try again in a moment."};
        for (int i = 0; i < statuses.length; i++) {
            GradeReport r = GRADER.grade(q, TestScope.VISIBLE, with(statuses[i], "A"), "src");
            assertFalse(r.passed(), statuses[i].name());
            assertEquals(summaries[i], r.summary(), statuses[i].name());
        }
    }

    @Test
    void stdoutIsCutAt4000Characters() {
        Quest q = stdoutQuest(List.of(exact("a", "A")), List.of());
        GradeReport r = GRADER.grade(q, TestScope.VISIBLE, ok("x".repeat(5000)), "src");
        assertEquals(4000, r.stdout().length());
    }

    @Test
    void commonMistakesAddHintsAndTotalIsCappedAtThree() {
        List<CommonMistake> mistakes = List.of(
                new CommonMistake(MistakeSource.STDOUT, "Total: 7", "M-stdout"),
                new CommonMistake(MistakeSource.SOURCE, "Total: 12", "M-source"),
                new CommonMistake(MistakeSource.COMPILER, "never", "M-compiler"),
                new CommonMistake(MistakeSource.STDOUT, "Total", "M-extra"),
                new CommonMistake(MistakeSource.STDOUT, "7", "M-fourth"));
        Quest q = quest(ChallengeKind.STDOUT, Profile.BASIC, List.of(exact("t", "Total: 12")), List.of(), mistakes);
        GradeReport r = GRADER.grade(q, TestScope.VISIBLE, ok("Total: 7"), "System.out.println(\"Total: 12\");");
        assertEquals(List.of("M-stdout", "M-source", "M-extra"), r.friendlyHints());
    }

    @Test
    void passingRunShowsNoMistakeHints() {
        List<CommonMistake> mistakes = List.of(new CommonMistake(MistakeSource.STDOUT, "Total", "Close!"));
        Quest q = quest(ChallengeKind.STDOUT, Profile.BASIC, List.of(exact("t", "Total: 12")), List.of(), mistakes);
        GradeReport r = GRADER.grade(q, TestScope.VISIBLE, ok("Total: 12"), "src");
        assertTrue(r.passed());
        assertTrue(r.friendlyHints().isEmpty());
    }
}
