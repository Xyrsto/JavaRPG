package dev.javarpg.runner.grading;

import static dev.javarpg.runner.grading.GradeText.cut;
import static dev.javarpg.runner.grading.GradeText.nz;

import dev.javarpg.core.contract.ChallengeKind;
import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.Grader;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestOutcome;
import dev.javarpg.core.contract.TestScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares one sandbox run against a quest's tests by wiring the grading collaborators together.
 * Hidden expected values never leave the grader.
 */
public final class DefaultGrader implements Grader {

    private final HarnessResultParser parser;
    private final OutputScrubber scrubber;
    private final TestEvaluator evaluator;
    private final HintBuilder hintBuilder;
    private final SummaryMessages summaries;

    public DefaultGrader() {
        this(new HarnessResultParser(), new OutputScrubber(), new TestEvaluator(), new HintBuilder(),
                new SummaryMessages());
    }

    public DefaultGrader(HarnessResultParser parser, OutputScrubber scrubber, TestEvaluator evaluator,
                         HintBuilder hintBuilder, SummaryMessages summaries) {
        this.parser = parser;
        this.scrubber = scrubber;
        this.evaluator = evaluator;
        this.hintBuilder = hintBuilder;
        this.summaries = summaries;
    }

    /** Tests in scope, visible first, then hidden when the scope is ALL. */
    public static List<TestCase> inScope(Quest quest, TestScope scope) {
        return TestSelection.inScope(quest, scope);
    }

    /** The in-scope tests that become harness calls, in harness index order. */
    public static List<TestCase> callTestsInScope(Quest quest, TestScope scope) {
        return TestSelection.callTestsInScope(quest, scope);
    }

    @Override
    public GradeReport grade(Quest quest, TestScope scope, SandboxResult result, String playerSource) {
        boolean harness = quest.kind() == ChallengeKind.METHOD_RETURN;
        String stdoutRaw = nz(result.stdout());
        String stdoutNorm = OutputNormalizer.normalize(stdoutRaw);
        String compilerOutput = harness ? scrubber.scrubCompilerOutput(quest, nz(result.compilerOutput()))
                : nz(result.compilerOutput());
        String stderr = nz(result.stderr());
        boolean ok = result.status() == SandboxStatus.OK;
        Map<Integer, HarnessResult> results = ok && harness ? parser.parse(stdoutNorm) : new HashMap<>();
        RunContext context = new RunContext(stdoutNorm, results, playerSource);

        List<TestOutcome> outcomes = new ArrayList<>();
        int callIndex = 0;
        int passedCount = 0;
        for (GradedTest graded : TestSelection.plan(quest, scope)) {
            int index = -1;
            if (graded.test().call() != null) {
                index = callIndex;
                callIndex++;
            }
            TestOutcome outcome = ok ? evaluator.evaluate(graded, index, context) : evaluator.notRun(graded);
            outcomes.add(outcome);
            if (outcome.passed()) {
                passedCount++;
            }
        }
        boolean passed = ok && passedCount == outcomes.size();
        String summary = summaries.summary(result.status(), scope, passed, passedCount, outcomes.size());
        // Hints help after a failure; a fully passing run must not show "close!" style hints.
        List<String> hints = passed ? List.of()
                : hintBuilder.build(quest, compilerOutput, stderr, stdoutNorm, nz(playerSource));
        // Protocol lines and the nonce never leave the grader (F-10): they would reveal hidden test counts.
        return new GradeReport(passed, result.status(), compilerOutput,
                cut(scrubber.scrubStdout(stdoutRaw, harness)), cut(stderr),
                List.copyOf(outcomes), List.copyOf(hints), summary, result.durationMillis());
    }
}
