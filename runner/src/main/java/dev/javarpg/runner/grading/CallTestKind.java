package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestOutcome;

/** METHOD_RETURN tests: compares the value the harness reported for this call with the expected value. */
final class CallTestKind implements TestKind {

    @Override
    public boolean supports(TestCase test) {
        return test.call() != null;
    }

    @Override
    public TestOutcome evaluate(GradedTest g, int callIndex, RunContext context) {
        HarnessResult r = context.results().get(callIndex);
        boolean pass = false;
        String actual = null;
        String message;
        if (r == null) {
            message = g.hidden() ? TestMessages.NOT_RUN_HIDDEN : "No result (the program stopped before this test).";
        } else if (r.exception()) {
            message = g.hidden() ? "Your method threw an exception." : "Your method threw an exception: " + r.value();
        } else {
            pass = OutputNormalizer.normalize(r.value()).equals(OutputNormalizer.normalize(g.test().expected()));
            actual = g.hidden() ? null : GradeText.cut(OutputNormalizer.normalize(r.value()));
            message = TestMessages.outcome(pass, g.hidden());
        }
        return new TestOutcome(g.displayName(), g.hidden(), pass, g.shownExpected(), actual, message);
    }
}
