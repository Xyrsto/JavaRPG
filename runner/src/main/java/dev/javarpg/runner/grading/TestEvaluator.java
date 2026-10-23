package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.TestOutcome;
import java.util.List;

/** Picks the {@link TestKind} for each test and builds the outcome, or a "did not run" outcome. */
public final class TestEvaluator {

    /** Order matters: the first kind that supports a test judges it; the stdout kind is the fallback. */
    private final List<TestKind> kinds = List.of(new CallTestKind(), new SourceRegexTestKind(), new StdoutTestKind());

    TestOutcome evaluate(GradedTest graded, int callIndex, RunContext context) {
        for (TestKind kind : kinds) {
            if (kind.supports(graded.test())) {
                return kind.evaluate(graded, callIndex, context);
            }
        }
        throw new IllegalStateException("No test kind for " + graded.test().name());
    }

    /** The outcome for a test whose run never finished OK. */
    TestOutcome notRun(GradedTest graded) {
        return new TestOutcome(graded.displayName(), graded.hidden(), false, graded.shownExpected(), null,
                graded.hidden() ? TestMessages.NOT_RUN_HIDDEN : TestMessages.NOT_RUN_VISIBLE);
    }
}
