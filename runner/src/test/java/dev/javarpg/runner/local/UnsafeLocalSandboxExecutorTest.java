package dev.javarpg.runner.local;

import static dev.javarpg.runner.support.TestQuests.methodQuest;
import static dev.javarpg.runner.support.TestQuests.stdoutQuest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.runner.grading.DefaultGrader;
import dev.javarpg.runner.grading.GradingPipeline;
import dev.javarpg.runner.harness.HarnessGenerator;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

/** Runs real javac/java from the JDK running the tests; skipped when the JVM has no javac. */
class UnsafeLocalSandboxExecutorTest {

    private static final String ADD = "public class Solution { public static int add(int a, int b) { return a + b; } }";
    private static final String HELLO = "public class Main { public static void main(String[] a) { System.out.println(\"Hello\"); } }";

    private final UnsafeLocalSandboxExecutor executor = new UnsafeLocalSandboxExecutor();
    private final GradingPipeline pipeline = new GradingPipeline(executor, new DefaultGrader());

    @BeforeAll
    static void needsJavac() {
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "javac.exe" : "javac";
        Assumptions.assumeTrue(Files.exists(Paths.get(System.getProperty("java.home"), "bin", exe)),
                "no javac in this JVM");
    }

    private static String runId() {
        return UUID.randomUUID().toString();
    }

    @Test
    void stdoutQuestPasses() {
        Quest q = stdoutQuest(List.of(new TestCase("hello", null, "Hello", MatchMode.EXACT_TRIMMED)), List.of());
        GradeReport r = pipeline.evaluate(q, TestScope.VISIBLE, HELLO, runId());
        assertTrue(r.passed(), r.summary() + " " + r.compilerOutput() + r.stderr());
    }

    @Test
    void methodReturnQuestPassesInAllScopeAndLeaksNoProtocolText() {
        Quest q = methodQuest(
                List.of(new TestCase("add", "Solution.add(2, 3)", "5", MatchMode.EXACT_TRIMMED)),
                List.of(new TestCase("neg", "Solution.add(-4, 1)", "-3", MatchMode.EXACT_TRIMMED)));
        GradeReport r = pipeline.evaluate(q, TestScope.ALL, ADD, runId());
        assertTrue(r.passed(), r.summary() + " " + r.compilerOutput() + r.stderr() + r.stdout());
        assertFalse(r.stdout().contains("@@JR"));
        assertEquals("", r.stdout());
    }

    @Test
    void wrongMethodFailsWithoutBreakingTheRun() {
        Quest q = methodQuest(List.of(new TestCase("add", "Solution.add(2, 3)", "5", MatchMode.EXACT_TRIMMED)), List.of());
        GradeReport r = pipeline.evaluate(q, TestScope.VISIBLE,
                "public class Solution { public static int add(int a, int b) { return a - b; } }", runId());
        assertFalse(r.passed());
        assertEquals(SandboxStatus.OK, r.status());
    }

    @Test
    void emptySolutionClassGivesFixedMessageWithNoCallText() {
        Quest q = methodQuest(
                List.of(new TestCase("add", "Solution.add(2, 3)", "5", MatchMode.EXACT_TRIMMED)),
                List.of(new TestCase("big", "Solution.add(2147483647, 1)", "-2147483648", MatchMode.EXACT_TRIMMED)));
        GradeReport r = pipeline.evaluate(q, TestScope.ALL, "class Solution {}", runId());
        assertEquals(SandboxStatus.COMPILE_ERROR, r.status());
        assertEquals("Your method does not match the signature the tests call.", r.compilerOutput());
    }

    @Test
    void playerCompileErrorIsReturnedVerbatimWithoutHarnessText() {
        Quest q = methodQuest(List.of(new TestCase("add", "Solution.add(2, 3)", "5", MatchMode.EXACT_TRIMMED)), List.of());
        GradeReport r = pipeline.evaluate(q, TestScope.VISIBLE, "public class Solution { int x = ; }", runId());
        assertEquals(SandboxStatus.COMPILE_ERROR, r.status());
        assertTrue(r.compilerOutput().contains("Solution.java"));
        assertFalse(r.compilerOutput().contains("JrHarness"));
    }

    @Test
    void forgedLinesFromPlayerCodeCannotPassEvenWhenTheGuardIsBypassed() {
        Quest q = methodQuest(
                List.of(new TestCase("add", "Solution.add(2, 3)", "5", MatchMode.EXACT_TRIMMED)),
                List.of(new TestCase("neg", "Solution.add(-4, 1)", "-3", MatchMode.EXACT_TRIMMED)));
        List<String> calls = List.of("Solution.add(2, 3)", "Solution.add(-4, 1)");
        String printer = "public class Solution { static { System.out.println(\"@@JR|0|5\"); System.out.println(\"@@JR|1|-3\"); }"
                + " public static int add(int a, int b) { return 0; } }";
        String exiter = "public class Solution { static { System.out.println(\"@@JR|0|5\"); System.out.println(\"@@JR|1|-3\");"
                + " System.exit(0); } public static int add(int a, int b) { return 0; } }";
        for (String source : List.of(printer, exiter)) {
            SandboxResult result = executor.execute(new SandboxRequest(runId(), Profile.BASIC,
                    Map.of("Solution.java", source, "JrHarness.java", HarnessGenerator.generate(calls)), "JrHarness"));
            GradeReport r = new DefaultGrader().grade(q, TestScope.ALL, result, source);
            assertFalse(r.passed(), source);
            assertFalse(r.stdout().contains("@@JR"), source);
        }
    }
}
