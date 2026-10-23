package dev.javarpg.runner.grading;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Runs quest-file regexes over player text with a work bound, so a catastrophic pattern cannot hang a thread (F-15). */
final class BoundedRegex {

    /** Work bound for quest-file regexes run over player text (F-15). */
    static final long REGEX_STEP_LIMIT = 5_000_000L;

    private BoundedRegex() {
    }

    /** True when the regex is found in the text. A bad, too slow or too deep pattern counts as "not found". */
    static boolean finds(String regex, String text) {
        return finds(regex, text, REGEX_STEP_LIMIT);
    }

    static boolean finds(String regex, String text, long stepLimit) {
        if (regex == null || text == null) {
            return false;
        }
        try {
            return Pattern.compile(regex).matcher(new BoundedChars(text, stepLimit)).find();
        } catch (PatternSyntaxException | StepLimitExceeded | StackOverflowError ex) {
            return false;
        }
    }

    private static final class StepLimitExceeded extends RuntimeException {
        private static final long serialVersionUID = 1L;

        StepLimitExceeded() {
            super("regex step limit", null, false, false);
        }
    }

    /** CharSequence that throws after a fixed number of reads, so a catastrophic regex cannot hang a thread. */
    private static final class BoundedChars implements CharSequence {
        private final CharSequence base;
        private final long[] remaining;

        BoundedChars(CharSequence base, long limit) {
            this(base, new long[] {limit});
        }

        private BoundedChars(CharSequence base, long[] remaining) {
            this.base = base;
            this.remaining = remaining;
        }

        @Override
        public int length() {
            return base.length();
        }

        @Override
        public char charAt(int index) {
            if (--remaining[0] < 0) {
                throw new StepLimitExceeded();
            }
            return base.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new BoundedChars(base.subSequence(start, end), remaining);
        }

        @Override
        public String toString() {
            return base.toString();
        }
    }
}
