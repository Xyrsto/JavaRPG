package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestOutcome;

/**
 * One way of judging a test (harness call, source regex, stdout match). Adding a new kind means adding a
 * class and listing it in {@link TestEvaluator}; no existing kind changes.
 */
interface TestKind {

    boolean supports(TestCase test);

    /** callIndex is the test's harness index, or -1 when the test is not a harness call. */
    TestOutcome evaluate(GradedTest graded, int callIndex, RunContext context);
}
