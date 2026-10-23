package dev.javarpg.server.service;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.Evaluator;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.ProgressService;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.core.contract.RecordedAttempt;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.contract.TestOutcome;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.core.rules.XpRules;
import dev.javarpg.server.dto.ApiDtos.CompletionView;
import dev.javarpg.server.dto.ApiDtos.HintResponse;
import dev.javarpg.server.dto.ApiDtos.RunResponse;
import dev.javarpg.server.dto.ApiDtos.SubmitResponse;
import dev.javarpg.server.dto.ApiDtos.TestResultView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Run, Submit and Hint flows (plan 3.3, 5.8). Server authoritative: the client only sends source. */
@Service
public class SubmissionService {

    static final int MAX_SOURCE_CHARS = 20000;

    private final QuestRepository repository;
    private final ProgressService progress;
    private final Evaluator evaluator;
    private final RunGate runGate;
    private final QuestViewService quests;

    public SubmissionService(QuestRepository repository, ProgressService progress, Evaluator evaluator,
                             RunGate runGate, QuestViewService quests) {
        this.repository = repository;
        this.progress = progress;
        this.evaluator = evaluator;
        this.runGate = runGate;
        this.quests = quests;
    }

    /** Runs the visible tests only. Never completes a quest and never awards XP. */
    public RunResponse run(String playerId, String questId, String source) {
        Quest quest = prepare(playerId, questId, source);
        GradeReport report = grade(playerId, quest, TestScope.VISIBLE, source);
        record(playerId, quest, source, report, false);
        return new RunResponse(quest.id(), "VISIBLE", report.passed(), report.status().name(),
                report.compilerOutput(), report.stdout(), report.stderr(), report.summary(),
                report.friendlyHints(), views(report.tests()), report.durationMillis());
    }

    /** Runs every test. A pass completes the quest the first time and awards its XP. */
    public SubmitResponse submit(String playerId, String questId, String source) {
        Quest quest = prepare(playerId, questId, source);
        GradeReport report = grade(playerId, quest, TestScope.ALL, source);
        RecordedAttempt attempt = record(playerId, quest, source, report, true);
        CompletionView completion = null;
        if (report.passed() && attempt != null) {
            completion = new CompletionView(attempt.firstCompletion(), attempt.xpAwarded(), attempt.totalXp(),
                    attempt.level(), attempt.levelUp(), quest.worldEffect(), quest.explanation(),
                    nextQuestId(quest.id()));
        }
        return new SubmitResponse(quest.id(), "ALL", report.passed(), report.status().name(),
                report.compilerOutput(), report.stdout(), report.stderr(), report.summary(),
                report.friendlyHints(), views(report.tests()), report.durationMillis(), completion);
    }

    /** Unlocks the next hint. Allowed after a failed attempt or once the quest is completed. */
    public HintResponse nextHint(String playerId, String questId) {
        Quest quest = quests.requireQuest(questId);
        Set<String> completed = quests.completedSet(playerId);
        if ("LOCKED".equals(quests.statusOf(questId, completed))) {
            throw new GameException(ErrorCode.QUEST_LOCKED, quests.lockMessage(questId, completed));
        }
        int hintNumber = progress.unlockNextHint(playerId, questId, quest.hints().size());
        return new HintResponse(hintNumber, quest.hints().get(hintNumber - 1), hintNumber, quest.hints().size());
    }

    private Quest prepare(String playerId, String questId, String source) {
        if (source == null || source.isBlank()) {
            throw new GameException(ErrorCode.BAD_REQUEST, "Your code is empty. Write some Java first.");
        }
        if (source.length() > MAX_SOURCE_CHARS) {
            throw new GameException(ErrorCode.SOURCE_TOO_LARGE,
                    "That code is too long. Keep it under " + MAX_SOURCE_CHARS + " characters.");
        }
        Quest quest = quests.requireQuest(questId);
        Set<String> completed = quests.completedSet(playerId);
        if ("LOCKED".equals(quests.statusOf(questId, completed))) {
            throw new GameException(ErrorCode.QUEST_LOCKED, quests.lockMessage(questId, completed));
        }
        return quest;
    }

    private GradeReport grade(String playerId, Quest quest, TestScope scope, String source) {
        runGate.acquire(playerId);
        try {
            return evaluator.evaluate(quest, scope, source, UUID.randomUUID().toString());
        } finally {
            runGate.release(playerId);
        }
    }

    /**
     * Stores the attempt. A SANDBOX_ERROR is the server's fault, so it is not a failed attempt and nothing
     * is stored (the editor keeps the player's text on the client).
     */
    private RecordedAttempt record(String playerId, Quest quest, String source, GradeReport report,
                                   boolean complete) {
        if (report.status() == SandboxStatus.SANDBOX_ERROR) {
            return null;
        }
        return progress.recordAttempt(playerId, quest.id(), source, report.passed(), complete,
                XpRules.xpFor(quest.id()));
    }

    private String nextQuestId(String questId) {
        List<Quest> all = repository.findAllOrdered();
        for (int i = 0; i + 1 < all.size(); i++) {
            if (all.get(i).id().equals(questId)) {
                return all.get(i + 1).id();
            }
        }
        return null;
    }

    private static List<TestResultView> views(List<TestOutcome> outcomes) {
        List<TestResultView> views = new ArrayList<>();
        for (TestOutcome o : outcomes) {
            views.add(new TestResultView(o.name(), o.hidden(), o.passed(), o.expected(), o.actual(), o.message()));
        }
        return views;
    }
}
