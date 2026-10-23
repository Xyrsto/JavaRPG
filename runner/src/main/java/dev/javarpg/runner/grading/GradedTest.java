package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.TestCase;

/** One test in a run. Hidden tests are shown only by number, and their expected value is never shown. */
record GradedTest(TestCase test, boolean hidden, int hiddenNumber) {

    String displayName() {
        return hidden ? "Hidden test " + hiddenNumber : test.name();
    }

    /** Value shown as "expected": never for hidden tests, never a SOURCE_REGEX pattern (F-11). */
    String shownExpected() {
        if (hidden || test.match() == MatchMode.SOURCE_REGEX) {
            return null;
        }
        return test.expected();
    }
}
