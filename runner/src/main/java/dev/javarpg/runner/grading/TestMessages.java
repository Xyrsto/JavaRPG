package dev.javarpg.runner.grading;

/** Player-facing messages for single tests. Hidden tests get vaguer wording so they reveal nothing. */
final class TestMessages {

    static final String PASSED = "Passed.";
    static final String VISIBLE_FAILED = "Not quite. Compare your result with the expected value.";
    static final String HIDDEN_FAILED = "This hidden test did not pass.";
    static final String NOT_RUN_VISIBLE = "This test did not run because the code did not finish.";
    static final String NOT_RUN_HIDDEN = "This test did not run.";
    static final String SOURCE_FAILED = "Your code does not match what this test expects.";

    private TestMessages() {
    }

    /** PASSED, or the failure text that fits the test's visibility. */
    static String outcome(boolean pass, boolean hidden) {
        return pass ? PASSED : (hidden ? HIDDEN_FAILED : VISIBLE_FAILED);
    }
}
