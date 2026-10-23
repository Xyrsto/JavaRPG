package dev.javarpg.server.service;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.ProgressService;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.QuestRecord;
import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.rules.Curriculum;
import dev.javarpg.core.rules.UnlockRules;
import dev.javarpg.core.rules.XpRules;
import dev.javarpg.server.dto.ApiDtos.QuestDetailView;
import dev.javarpg.server.dto.ApiDtos.QuestListResponse;
import dev.javarpg.server.dto.ApiDtos.QuestSummary;
import dev.javarpg.server.dto.ApiDtos.VisibleTestView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Quest status (LOCKED, AVAILABLE, COMPLETED) and the quest list and detail views. Secrets stay out of views. */
@Service
public class QuestViewService {

    private final QuestRepository repository;
    private final ProgressService progress;

    public QuestViewService(QuestRepository repository, ProgressService progress) {
        this.repository = repository;
        this.progress = progress;
    }

    public Set<String> completedSet(String playerId) {
        return new HashSet<>(progress.getPlayerProgress(playerId).completedQuestIds());
    }

    public String statusOf(String questId, Set<String> completed) {
        if (completed.contains(questId)) {
            return "COMPLETED";
        }
        List<String> ids = new ArrayList<>();
        for (Quest quest : repository.findAllOrdered()) {
            ids.add(quest.id());
        }
        return UnlockRules.isUnlocked(ids, completed, questId) ? "AVAILABLE" : "LOCKED";
    }

    /** Friendly reason for a locked quest: names the first unfinished earlier quest. */
    public String lockMessage(String questId, Set<String> completed) {
        for (Quest quest : repository.findAllOrdered()) {
            if (quest.id().equals(questId)) {
                break;
            }
            if (!completed.contains(quest.id())) {
                return "Finish \"" + quest.title() + "\" first.";
            }
        }
        return "Finish the earlier quests first.";
    }

    public Quest requireQuest(String questId) {
        return repository.findById(questId).orElseThrow(
                () -> new GameException(ErrorCode.QUEST_NOT_FOUND, "There is no quest with that id."));
    }

    public QuestListResponse list(String playerId) {
        Set<String> completed = completedSet(playerId);
        List<QuestSummary> items = new ArrayList<>();
        for (Quest quest : repository.findAllOrdered()) {
            items.add(new QuestSummary(quest.id(), quest.region(), regionName(quest.id()), quest.title(),
                    quest.concept(), statusOf(quest.id(), completed), XpRules.xpFor(quest.id())));
        }
        return new QuestListResponse(items);
    }

    public QuestDetailView detail(String playerId, String questId) {
        Quest quest = requireQuest(questId);
        Set<String> completed = completedSet(playerId);
        String status = statusOf(questId, completed);
        if ("LOCKED".equals(status)) {
            throw new GameException(ErrorCode.QUEST_LOCKED, lockMessage(questId, completed));
        }
        QuestRecord record = progress.getQuestRecord(playerId, questId);
        boolean done = "COMPLETED".equals(status);

        List<VisibleTestView> visible = new ArrayList<>();
        for (TestCase test : quest.visibleTests()) {
            String expected = test.match() == MatchMode.SOURCE_REGEX ? null : test.expected();
            visible.add(new VisibleTestView(test.name(), test.call(), expected));
        }
        List<String> hints = new ArrayList<>();
        for (int i = 0; i < record.hintsUnlocked() && i < quest.hints().size(); i++) {
            hints.add(quest.hints().get(i));
        }

        return new QuestDetailView(quest.id(), quest.title(), quest.concept(), quest.story(), quest.goal(),
                quest.kind().name(), quest.profile().name(), status, XpRules.xpFor(quest.id()),
                quest.starterCode(), record.lastSource(), visible, quest.hints().size(),
                record.hintsUnlocked(), hints, record.failedAttempts(),
                done ? quest.explanation() : null,
                done ? quest.referenceSolution() : null,
                done ? quest.worldEffect() : null);
    }

    private static String regionName(String questId) {
        return Curriculum.regionOf(questId).name();
    }
}
