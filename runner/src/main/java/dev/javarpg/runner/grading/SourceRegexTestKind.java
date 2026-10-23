package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestOutcome;
import dev.javarpg.runner.guard.SourceScanner;

/** SOURCE_REGEX tests: the pattern must be found in the player's source with comments removed. */
final class SourceRegexTestKind implements TestKind {

    @Override
    public boolean supports(TestCase test) {
        return test.match() == MatchMode.SOURCE_REGEX;
    }

    @Override
    public TestOutcome evaluate(GradedTest g, int callIndex, RunContext context) {
        boolean pass = BoundedRegex.finds(g.test().expected(), SourceScanner.stripComments(context.playerSource()));
        String message = pass ? TestMessages.PASSED
                : (g.hidden() ? TestMessages.HIDDEN_FAILED : TestMessages.SOURCE_FAILED);
        return new TestOutcome(g.displayName(), g.hidden(), pass, g.shownExpected(), null, message);
    }
}
