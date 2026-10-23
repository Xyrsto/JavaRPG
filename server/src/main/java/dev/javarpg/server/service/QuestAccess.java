package dev.javarpg.server.service;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.ProgressService;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.core.rules.UnlockRules;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Who may open which quest: lookup, status (LOCKED, AVAILABLE, COMPLETED), the lock message and the
 * curriculum order. The single place that decides "this quest is locked for that player".
 */
@Service
public class QuestAccess {

    private final QuestRepository repository;
    private final ProgressService progress;

    public QuestAccess(QuestRepository repository, ProgressService progress) {
        this.repository = repository;
        this.progress = progress;
    }

    public Set<String> completedSet(String playerId) {
        return new HashSet<>(progress.getPlayerProgress(playerId).completedQuestIds());
    }

    public QuestStatus statusOf(String questId, Set<String> completed) {
        if (completed.contains(questId)) {
            return QuestStatus.COMPLETED;
        }
        List<String> ids = repository.findAllOrdered().stream().map(Quest::id).toList();
        return UnlockRules.isUnlocked(ids, completed, questId) ? QuestStatus.AVAILABLE : QuestStatus.LOCKED;
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

    /** The quest's status for this player; throws QUEST_LOCKED when the player may not open it yet. */
    public QuestStatus requireNotLocked(String questId, Set<String> completed) {
        QuestStatus status = statusOf(questId, completed);
        if (status == QuestStatus.LOCKED) {
            throw new GameException(ErrorCode.QUEST_LOCKED, lockMessage(questId, completed));
        }
        return status;
    }

    /** Quest lookup, then player lookup, then lock check (this order decides which error wins). */
    public Quest requireOpenQuest(String playerId, String questId) {
        Quest quest = requireQuest(questId);
        requireNotLocked(questId, completedSet(playerId));
        return quest;
    }

    /** Id of the quest after this one in curriculum order, or null for the last one. */
    public String nextQuestId(String questId) {
        List<Quest> all = repository.findAllOrdered();
        for (int i = 0; i + 1 < all.size(); i++) {
            if (all.get(i).id().equals(questId)) {
                return all.get(i + 1).id();
            }
        }
        return null;
    }
}
