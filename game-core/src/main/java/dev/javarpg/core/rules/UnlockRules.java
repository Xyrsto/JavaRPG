package dev.javarpg.core.rules;

import java.util.List;
import java.util.Set;

public final class UnlockRules {
    private UnlockRules() { }

    /**
     * A quest is unlocked iff every quest that precedes it in `orderedLoadedQuestIds` is in `completed`.
     * The first loaded quest is always unlocked. Unknown quest id -> false.
     */
    public static boolean isUnlocked(List<String> orderedLoadedQuestIds, Set<String> completed, String questId) {
        if (questId == null) {
            return false;
        }
        int index = orderedLoadedQuestIds.indexOf(questId);
        if (index < 0) {
            return false;
        }
        for (int i = 0; i < index; i++) {
            if (!completed.contains(orderedLoadedQuestIds.get(i))) {
                return false;
            }
        }
        return true;
    }
}
