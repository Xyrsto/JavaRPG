package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestOutcome;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiPredicate;

/** STDOUT tests: compares the run's normalized stdout with the expected text by the test's match mode. */
final class StdoutTestKind implements TestKind {

    /** (normalized stdout, normalized expected) to pass/fail. A new stdout mode is one more entry. */
    private static final Map<MatchMode, BiPredicate<String, String>> MATCHERS = new EnumMap<>(MatchMode.class);

    static {
        MATCHERS.put(MatchMode.EXACT_TRIMMED, String::equals);
        MATCHERS.put(MatchMode.CONTAINS, String::contains);
        MATCHERS.put(MatchMode.NOT_CONTAINS, (stdout, expected) -> !stdout.contains(expected));
    }

    /** Fallback kind: takes every test the more specific kinds do not claim. */
    @Override
    public boolean supports(TestCase test) {
        return true;
    }

    @Override
    public TestOutcome evaluate(GradedTest g, int callIndex, RunContext context) {
        String expectedNorm = OutputNormalizer.normalize(g.test().expected());
        String stdout = context.stdoutNorm();
        String actual = g.hidden() ? null : GradeText.cut(stdout);
        boolean pass = MATCHERS.get(g.test().match()).test(stdout, expectedNorm);
        return new TestOutcome(g.displayName(), g.hidden(), pass, g.shownExpected(), actual,
                TestMessages.outcome(pass, g.hidden()));
    }
}
