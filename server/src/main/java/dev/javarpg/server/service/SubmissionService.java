package dev.javarpg.server.service;

import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.ProgressService;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.RecordedAttempt;
import dev.javarpg.core.contract.TestOutcome;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.server.dto.ApiDtos.CompletionView;
import dev.javarpg.server.dto.ApiDtos.HintResponse;
import dev.javarpg.server.dto.ApiDtos.RunResponse;
import dev.javarpg.server.dto.ApiDtos.SubmitResponse;
import dev.javarpg.server.dto.ApiDtos.TestResultView;
import java.util.List;
import org.springframework.stereotype.Service;

/** Run, Submit and Hint flows (plan 3.3, 5.8). Server authoritative: the client only sends source. */
@Service
public class SubmissionService {

    private final ProgressService progress;
    private final QuestAccess access;
    private final GatedEvaluator evaluator;
    private final AttemptRecorder recorder;

    public SubmissionService(ProgressService progress, QuestAccess access, GatedEvaluator evaluator,
                             AttemptRecorder recorder) {
        this.progress = progress;
        this.access = access;
        this.evaluator = evaluator;
        this.recorder = recorder;
    }

    /** Runs the visible tests only. Never completes a quest and never awards XP. */
    public RunResponse run(String playerId, String questId, String source) {
        Quest quest = prepare(playerId, questId, source);
        GradeReport report = evaluator.evaluate(playerId, quest, TestScope.VISIBLE, source);
        recorder.record(playerId, quest, source, report, false);
        return new RunResponse(quest.id(), "VISIBLE", report.passed(), report.status().name(),
                report.compilerOutput(), report.stdout(), report.stderr(), report.summary(),
                report.friendlyHints(), views(report.tests()), report.durationMillis());
    }

    /** Runs every test. A pass completes the quest the first time and awards its XP. */
    public SubmitResponse submit(String playerId, String questId, String source) {
        Quest quest = prepare(playerId, questId, source);
        GradeReport report = evaluator.evaluate(playerId, quest, TestScope.ALL, source);
        RecordedAttempt attempt = recorder.record(playerId, quest, source, report, true);
        CompletionView completion = null;
        if (report.passed() && attempt != null) {
            completion = new CompletionView(attempt.firstCompletion(), attempt.xpAwarded(), attempt.totalXp(),
                    attempt.level(), attempt.levelUp(), quest.worldEffect(), quest.explanation(),
                    access.nextQuestId(quest.id()));
        }
        return new SubmitResponse(quest.id(), "ALL", report.passed(), report.status().name(),
                report.compilerOutput(), report.stdout(), report.stderr(), report.summary(),
                report.friendlyHints(), views(report.tests()), report.durationMillis(), completion);
    }

    /** Unlocks the next hint. Allowed after a failed attempt or once the quest is completed. */
    public HintResponse nextHint(String playerId, String questId) {
        Quest quest = access.requireOpenQuest(playerId, questId);
        int hintNumber = progress.unlockNextHint(playerId, questId, quest.hints().size());
        return new HintResponse(hintNumber, quest.hints().get(hintNumber - 1), hintNumber, quest.hints().size());
    }

    private Quest prepare(String playerId, String questId, String source) {
        SourcePolicy.require(source);
        return access.requireOpenQuest(playerId, questId);
    }

    private static List<TestResultView> views(List<TestOutcome> outcomes) {
        return outcomes.stream()
                .map(o -> new TestResultView(o.name(), o.hidden(), o.passed(), o.expected(), o.actual(),
                        o.message()))
                .toList();
    }
}
