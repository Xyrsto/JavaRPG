package dev.javarpg.runner.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestOutcome;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestEvaluatorTest {

    private final TestEvaluator evaluator = new TestEvaluator();

    private static RunContext ctx(String stdout, Map<Integer, HarnessResult> results, String source) {
        return new RunContext(stdout, results, source);
    }

    @Test
    void hiddenFailureRevealsNeitherExpectedNorActual() {
        GradedTest g = new GradedTest(new TestCase("t", null, "secret", MatchMode.EXACT_TRIMMED), true, 2);
        TestOutcome o = evaluator.evaluate(g, -1, ctx("other", Map.of(), ""));
        assertFalse(o.passed());
        assertEquals("Hidden test 2", o.name());
        assertNull(o.expected());
        assertNull(o.actual());
        assertEquals(TestMessages.HIDDEN_FAILED, o.message());
    }

    @Test
    void sourceRegexPatternIsNeverShown() {
        GradedTest g = new GradedTest(new TestCase("t", null, "int\\s+x", MatchMode.SOURCE_REGEX), false, 0);
        TestOutcome o = evaluator.evaluate(g, -1, ctx("", Map.of(), "int x = 1;"));
        assertTrue(o.passed());
        assertNull(o.expected());
    }

    @Test
    void callTestUsesTheHarnessResultAtItsIndex() {
        GradedTest g = new GradedTest(new TestCase("t", "f(1)", "5", MatchMode.EXACT_TRIMMED), false, 0);
        assertTrue(evaluator.evaluate(g, 0, ctx("", Map.of(0, new HarnessResult(false, "5")), "")).passed());
        TestOutcome missing = evaluator.evaluate(g, 1, ctx("", Map.of(0, new HarnessResult(false, "5")), ""));
        assertFalse(missing.passed());
        assertEquals("No result (the program stopped before this test).", missing.message());
    }

    @Test
    void notContainsPassesWhenTextIsAbsent() {
        GradedTest g = new GradedTest(new TestCase("t", null, "bad", MatchMode.NOT_CONTAINS), false, 0);
        assertTrue(evaluator.evaluate(g, -1, ctx("good", Map.of(), "")).passed());
        assertFalse(evaluator.evaluate(g, -1, ctx("so bad", Map.of(), "")).passed());
    }

    @Test
    void notRunUsesVisibilityAppropriateWording() {
        GradedTest visible = new GradedTest(new TestCase("t", null, "x", MatchMode.CONTAINS), false, 0);
        GradedTest hidden = new GradedTest(new TestCase("t", null, "x", MatchMode.CONTAINS), true, 1);
        assertEquals(TestMessages.NOT_RUN_VISIBLE, evaluator.notRun(visible).message());
        assertEquals(TestMessages.NOT_RUN_HIDDEN, evaluator.notRun(hidden).message());
    }
}
