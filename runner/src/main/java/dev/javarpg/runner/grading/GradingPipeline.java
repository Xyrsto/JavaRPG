package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.ChallengeKind;
import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.Evaluator;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.Grader;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.runner.guard.SourceGuard;
import dev.javarpg.runner.harness.HarnessGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Guard, then build the sandbox request, run it once, then grade the result outside the sandbox. */
public final class GradingPipeline implements Evaluator {

    private static final String BLOCKED_SUMMARY = "That code is not allowed in this quest.";

    private final SandboxExecutor executor;
    private final Grader grader;

    public GradingPipeline(SandboxExecutor executor, Grader grader) {
        this.executor = executor;
        this.grader = grader;
    }

    @Override
    public GradeReport evaluate(Quest quest, TestScope scope, String playerSource, String runId) {
        if (quest.profile() != Profile.BASIC) {
            throw new GameException(ErrorCode.QUEST_PROFILE_UNSUPPORTED, "This challenge is not available yet.");
        }
        SourceGuard.Verdict verdict = SourceGuard.check(playerSource, quest.profile());
        if (!verdict.allowed()) {
            return blocked(verdict.reason());
        }
        SandboxRequest request = buildRequest(quest, scope, playerSource, runId);
        SandboxResult result = executor.execute(request);
        return grader.grade(quest, scope, result, playerSource);
    }

    private static SandboxRequest buildRequest(Quest quest, TestScope scope, String playerSource, String runId) {
        if (quest.kind() == ChallengeKind.STDOUT) {
            return new SandboxRequest(runId, quest.profile(), Map.of("Main.java", playerSource), "Main");
        }
        List<String> calls = new ArrayList<>();
        for (TestCase t : DefaultGrader.callTestsInScope(quest, scope)) {
            calls.add(t.call());
        }
        Map<String, String> sources = Map.of(
                "Solution.java", playerSource,
                "JrHarness.java", HarnessGenerator.generate(calls));
        return new SandboxRequest(runId, quest.profile(), sources, "JrHarness");
    }

    private static GradeReport blocked(String reason) {
        return new GradeReport(false, SandboxStatus.RUNTIME_ERROR, "", "", "", List.of(),
                List.of(reason), BLOCKED_SUMMARY, 0L);
    }
}
