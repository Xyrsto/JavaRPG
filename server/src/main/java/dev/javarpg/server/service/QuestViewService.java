package dev.javarpg.server.service;

import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.ProgressService;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.QuestRecord;
import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.rules.Curriculum;
import dev.javarpg.core.rules.XpRules;
import dev.javarpg.server.dto.ApiDtos.QuestDetailView;
import dev.javarpg.server.dto.ApiDtos.QuestListResponse;
import dev.javarpg.server.dto.ApiDtos.QuestSummary;
import dev.javarpg.server.dto.ApiDtos.VisibleTestView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** The quest list and detail views. Secrets (hidden tests, unearned hints and solutions) stay out of them. */
@Service
public class QuestViewService {

    private final QuestRepository repository;
    private final ProgressService progress;
    private final QuestAccess access;

    public QuestViewService(QuestRepository repository, ProgressService progress, QuestAccess access) {
        this.repository = repository;
        this.progress = progress;
        this.access = access;
    }

    public QuestListResponse list(String playerId) {
        Set<String> completed = access.completedSet(playerId);
        List<QuestSummary> items = new ArrayList<>();
        for (Quest quest : repository.findAllOrdered()) {
            items.add(new QuestSummary(quest.id(), quest.region(), regionName(quest.id()), quest.title(),
                    quest.concept(), access.statusOf(quest.id(), completed).name(), XpRules.xpFor(quest.id())));
        }
        return new QuestListResponse(items);
    }

    public QuestDetailView detail(String playerId, String questId) {
        Quest quest = access.requireQuest(questId);
        Set<String> completed = access.completedSet(playerId);
        QuestStatus status = access.requireNotLocked(questId, completed);
        QuestRecord record = progress.getQuestRecord(playerId, questId);
        boolean done = status == QuestStatus.COMPLETED;

        return new QuestDetailView(quest.id(), quest.title(), quest.concept(), quest.story(), quest.goal(),
                quest.kind().name(), quest.profile().name(), status.name(), XpRules.xpFor(quest.id()),
                quest.starterCode(), record.lastSource(), visibleTests(quest), quest.hints().size(),
                record.hintsUnlocked(), unlockedHints(quest, record), record.failedAttempts(),
                done ? quest.explanation() : null,
                done ? quest.referenceSolution() : null,
                done ? quest.worldEffect() : null);
    }

    /** SOURCE_REGEX expectations are patterns, not output, so they are not shown to the player. */
    private static List<VisibleTestView> visibleTests(Quest quest) {
        List<VisibleTestView> visible = new ArrayList<>();
        for (TestCase test : quest.visibleTests()) {
            String expected = test.match() == MatchMode.SOURCE_REGEX ? null : test.expected();
            visible.add(new VisibleTestView(test.name(), test.call(), expected));
        }
        return visible;
    }

    private static List<String> unlockedHints(Quest quest, QuestRecord record) {
        List<String> hints = new ArrayList<>();
        for (int i = 0; i < record.hintsUnlocked() && i < quest.hints().size(); i++) {
            hints.add(quest.hints().get(i));
        }
        return hints;
    }

    private static String regionName(String questId) {
        return Curriculum.regionOf(questId).name();
    }
}
