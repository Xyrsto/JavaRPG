package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.ChallengeKind;
import dev.javarpg.core.contract.CommonMistake;
import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.Grader;
import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestOutcome;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.runner.guard.SourceScanner;
import dev.javarpg.runner.harness.HarnessProtocol;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Compares one sandbox run against a quest's tests. Hidden expected values never leave this class. */
public final class DefaultGrader implements Grader {

    static final int CUT = 4000;
    static final int MAX_HINTS = 3;

    /** Body of a result line after the "@@JR-nonce|" prefix. */
    private static final Pattern RESULT_BODY = Pattern.compile("(\\d{1,6})(!EXC)?\\|(.*)", Pattern.DOTALL);
    /** Work bound for quest-file regexes run over player text (F-15). */
    static final long REGEX_STEP_LIMIT = 5_000_000L;
    private static final String PASSED = "Passed.";
    private static final String VISIBLE_FAILED = "Not quite. Compare your result with the expected value.";
    private static final String HIDDEN_FAILED = "This hidden test did not pass.";
    private static final String NOT_RUN_VISIBLE = "This test did not run because the code did not finish.";
    private static final String NOT_RUN_HIDDEN = "This test did not run.";
    private static final String SOURCE_FAILED = "Your code does not match what this test expects.";

    private record Entry(TestCase test, boolean hidden, int hiddenNumber) {
    }

    private record Result(boolean exception, String value) {
    }

    /** Tests in scope, visible first, then hidden when the scope is ALL. */
    public static List<TestCase> inScope(Quest quest, TestScope scope) {
        List<TestCase> list = new ArrayList<>(quest.visibleTests());
        if (scope == TestScope.ALL) {
            list.addAll(quest.hiddenTests());
        }
        return List.copyOf(list);
    }

    /** The in-scope tests that become harness calls, in harness index order. */
    public static List<TestCase> callTestsInScope(Quest quest, TestScope scope) {
        List<TestCase> list = new ArrayList<>();
        for (TestCase t : inScope(quest, scope)) {
            if (t.call() != null) {
                list.add(t);
            }
        }
        return List.copyOf(list);
    }

    @Override
    public GradeReport grade(Quest quest, TestScope scope, SandboxResult result, String playerSource) {
        boolean harness = quest.kind() == ChallengeKind.METHOD_RETURN;
        String stdoutRaw = nz(result.stdout());
        String stdoutNorm = OutputNormalizer.normalize(stdoutRaw);
        String compilerOutput = harness ? scrubCompilerOutput(quest, scope, nz(result.compilerOutput()))
                : nz(result.compilerOutput());
        String stderr = nz(result.stderr());
        boolean ok = result.status() == SandboxStatus.OK;
        Map<Integer, Result> results = ok && harness ? parseResults(stdoutNorm) : new HashMap<>();

        List<TestOutcome> outcomes = new ArrayList<>();
        int callIndex = 0;
        int passedCount = 0;
        for (Entry e : entries(quest, scope)) {
            int index = -1;
            if (e.test().call() != null) {
                index = callIndex;
                callIndex++;
            }
            TestOutcome outcome = ok
                    ? evaluate(e, index, stdoutNorm, results, playerSource)
                    : notRun(e);
            outcomes.add(outcome);
            if (outcome.passed()) {
                passedCount++;
            }
        }
        boolean passed = ok && passedCount == outcomes.size();
        String summary = summary(result.status(), scope, passed, passedCount, outcomes.size());
        // Hints help after a failure; a fully passing run must not show "close!" style hints.
        List<String> hints = passed ? List.of() : hints(quest, compilerOutput, stderr, stdoutNorm, nz(playerSource));
        // Protocol lines and the nonce never leave the grader (F-10): they would reveal hidden test counts.
        return new GradeReport(passed, result.status(), compilerOutput, cut(scrubStdout(stdoutRaw, harness)), cut(stderr),
                List.copyOf(outcomes), List.copyOf(hints), summary, result.durationMillis());
    }

    private static List<Entry> entries(Quest quest, TestScope scope) {
        List<Entry> list = new ArrayList<>();
        for (TestCase t : quest.visibleTests()) {
            list.add(new Entry(t, false, 0));
        }
        if (scope == TestScope.ALL) {
            int n = 0;
            for (TestCase t : quest.hiddenTests()) {
                n++;
                list.add(new Entry(t, true, n));
            }
        }
        return list;
    }

    private static TestOutcome evaluate(Entry e, int index, String stdoutNorm,
                                        Map<Integer, Result> results, String playerSource) {
        TestCase t = e.test();
        String name = e.hidden() ? "Hidden test " + e.hiddenNumber() : t.name();
        String expected = shownExpected(e);
        String actual = null;
        boolean pass;
        String message;

        if (t.call() != null) {
            Result r = results.get(index);
            if (r == null) {
                pass = false;
                message = e.hidden() ? NOT_RUN_HIDDEN : "No result (the program stopped before this test).";
            } else if (r.exception()) {
                pass = false;
                message = e.hidden() ? "Your method threw an exception." : "Your method threw an exception: " + r.value();
            } else {
                pass = OutputNormalizer.normalize(r.value()).equals(OutputNormalizer.normalize(t.expected()));
                actual = e.hidden() ? null : cut(OutputNormalizer.normalize(r.value()));
                message = pass ? PASSED : (e.hidden() ? HIDDEN_FAILED : VISIBLE_FAILED);
            }
        } else if (t.match() == MatchMode.SOURCE_REGEX) {
            pass = findsRegex(t.expected(), SourceScanner.stripComments(playerSource));
            message = pass ? PASSED : (e.hidden() ? HIDDEN_FAILED : SOURCE_FAILED);
        } else {
            String expectedNorm = OutputNormalizer.normalize(t.expected());
            actual = e.hidden() ? null : cut(stdoutNorm);
            if (t.match() == MatchMode.EXACT_TRIMMED) {
                pass = stdoutNorm.equals(expectedNorm);
            } else if (t.match() == MatchMode.CONTAINS) {
                pass = stdoutNorm.contains(expectedNorm);
            } else {
                pass = !stdoutNorm.contains(expectedNorm);
            }
            message = pass ? PASSED : (e.hidden() ? HIDDEN_FAILED : VISIBLE_FAILED);
        }
        return new TestOutcome(name, e.hidden(), pass, expected, actual, message);
    }

    private static TestOutcome notRun(Entry e) {
        String name = e.hidden() ? "Hidden test " + e.hiddenNumber() : e.test().name();
        String expected = shownExpected(e);
        return new TestOutcome(name, e.hidden(), false, expected, null,
                e.hidden() ? NOT_RUN_HIDDEN : NOT_RUN_VISIBLE);
    }

    /** Value shown as "expected": never for hidden tests, never a SOURCE_REGEX pattern (F-11). */
    private static String shownExpected(Entry e) {
        if (e.hidden() || e.test().match() == MatchMode.SOURCE_REGEX) {
            return null;
        }
        return e.test().expected();
    }

    /**
     * The first line is the run's nonce; only lines that start with "@@JR-nonce|" count. A duplicate
     * index means tampering, so nothing is accepted (the caller then fails every call test).
     */
    static Map<Integer, Result> parseResults(String text) {
        Map<Integer, Result> map = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return map;
        }
        String[] lines = text.split("\n", -1);
        String nonce = lines[0].trim();
        if (!HarnessProtocol.NONCE.matcher(nonce).matches()) {
            return map;
        }
        String prefix = HarnessProtocol.RESULT_PREFIX + "-" + nonce + "|";
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith(prefix)) {
                continue;
            }
            Matcher m = RESULT_BODY.matcher(line.substring(prefix.length()));
            if (!m.matches()) {
                continue;
            }
            int index = Integer.parseInt(m.group(1));
            if (map.containsKey(index)) {
                return new HashMap<>();
            }
            map.put(index, new Result(m.group(2) != null, unescape(m.group(3))));
        }
        return map;
    }

    /** Drops protocol lines (and, for harness runs, the nonce line) from text that goes into a report. */
    static String scrubStdout(String raw, boolean harness) {
        if (raw.isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            String bare = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (bare.contains(HarnessProtocol.RESULT_PREFIX)
                    || (harness && HarnessProtocol.NONCE.matcher(bare.trim()).matches())) {
                continue;
            }
            if (sb.length() > 0 || !line.isEmpty()) {
                sb.append(line).append('\n');
            }
        }
        return sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";
    }

    /** Belt and braces for F-10: javac output that mentions the harness or a call text is replaced. */
    private static String scrubCompilerOutput(Quest quest, TestScope scope, String output) {
        if (output.isEmpty()) {
            return output;
        }
        boolean leaks = output.contains("JrHarness") || output.contains(HarnessProtocol.RESULT_PREFIX);
        if (!leaks) {
            for (TestCase t : callTestsInScope(quest, TestScope.ALL)) {
                if (output.contains(t.call())) {
                    leaks = true;
                    break;
                }
            }
        }
        return leaks ? HarnessProtocol.HARNESS_COMPILE_MESSAGE : output;
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == 'n') {
                    sb.append('\n');
                    i++;
                    continue;
                }
                if (n == 'r') {
                    sb.append('\r');
                    i++;
                    continue;
                }
                if (n == '\\') {
                    sb.append('\\');
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static String summary(SandboxStatus status, TestScope scope, boolean passed, int passedCount, int total) {
        if (status != SandboxStatus.OK) {
            return statusSummary(status);
        }
        if (passed) {
            return scope == TestScope.ALL
                    ? "Challenge complete!"
                    : "All visible tests pass. Press Submit to finish the challenge.";
        }
        return "Almost! " + passedCount + " of " + total + " tests passed.";
    }

    private static String statusSummary(SandboxStatus status) {
        switch (status) {
            case COMPILE_ERROR:
                return "Your code did not compile yet. Read the message below, then try again.";
            case TIMEOUT:
                return "Your code ran for too long and was stopped. Look for a loop that never ends.";
            case OUTPUT_LIMIT:
                return "Your code printed far too much text and was stopped.";
            case MEMORY_LIMIT:
                return "Your code used too much memory and was stopped.";
            case RUNTIME_ERROR:
                return "Your code crashed while running. The error is shown below.";
            case SANDBOX_ERROR:
                return "The code runner is not available right now. Nothing was lost; try again in a moment.";
            default:
                return "Challenge complete!";
        }
    }

    private static List<String> hints(Quest quest, String compilerOutput, String stderr,
                                      String stdoutNorm, String playerSource) {
        Set<String> hints = new LinkedHashSet<>(ErrorTranslator.translate(compilerOutput, stderr));
        String source = SourceScanner.stripComments(playerSource);
        for (CommonMistake mistake : quest.commonMistakes()) {
            String text;
            if (mistake.source() == dev.javarpg.core.contract.MistakeSource.COMPILER) {
                text = compilerOutput;
            } else if (mistake.source() == dev.javarpg.core.contract.MistakeSource.STDOUT) {
                text = stdoutNorm;
            } else {
                text = source;
            }
            if (findsRegex(mistake.pattern(), text)) {
                hints.add(mistake.message());
            }
        }
        List<String> capped = new ArrayList<>();
        for (String hint : hints) {
            if (capped.size() >= MAX_HINTS) {
                break;
            }
            capped.add(hint);
        }
        return capped;
    }

    private static boolean findsRegex(String regex, String text) {
        if (regex == null || text == null) {
            return false;
        }
        try {
            return Pattern.compile(regex).matcher(new BoundedChars(text, REGEX_STEP_LIMIT)).find();
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

    static String cut(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > CUT ? s.substring(0, CUT) : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
