package dev.javarpg.core.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UnlockRulesTest {

    @Test
    void firstLoadedQuestIsAlwaysUnlocked() {
        assertTrue(UnlockRules.isUnlocked(List.of("V01", "V02"), Set.of(), "V01"));
    }

    @Test
    void secondQuestLockedUntilFirstCompleted() {
        List<String> ids = List.of("V01", "V02");
        assertFalse(UnlockRules.isUnlocked(ids, Set.of(), "V02"));
        assertTrue(UnlockRules.isUnlocked(ids, Set.of("V01"), "V02"));
    }

    @Test
    void unknownQuestIdIsNotUnlocked() {
        assertFalse(UnlockRules.isUnlocked(List.of("V01"), Set.of("V01"), "V99"));
    }

    @Test
    void gapInCompletionKeepsLaterQuestLocked() {
        List<String> ids = List.of("V01", "V02", "V03");
        assertFalse(UnlockRules.isUnlocked(ids, Set.of("V01", "V03"), "V03"));
    }
}
