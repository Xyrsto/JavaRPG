package dev.javarpg.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LevelRulesTest {

    @Test
    void levelForXpAtThresholds() {
        assertEquals(1, LevelRules.levelForXp(0));
        assertEquals(1, LevelRules.levelForXp(229));
        assertEquals(2, LevelRules.levelForXp(230));
        assertEquals(5, LevelRules.levelForXp(920));
        assertEquals(5, LevelRules.levelForXp(1149));
        assertEquals(6, LevelRules.levelForXp(1150));
        assertEquals(39, LevelRules.levelForXp(8257));
        assertEquals(40, LevelRules.levelForXp(8258));
        assertEquals(40, LevelRules.levelForXp(99999));
    }

    @Test
    void negativeXpIsLevelOne() {
        assertEquals(1, LevelRules.levelForXp(-5));
    }

    @Test
    void nextLevelXpValues() {
        assertEquals(230, LevelRules.nextLevelXp(0));
        assertEquals(230, LevelRules.nextLevelXp(200));
        assertEquals(0, LevelRules.nextLevelXp(8258));
    }

    @Test
    void levelsAreMonotonicOverXp() {
        int previous = LevelRules.levelForXp(0);
        for (int xp = 1; xp <= 9000; xp++) {
            int level = LevelRules.levelForXp(xp);
            assertTrue(level >= previous, "level dropped at xp " + xp);
            previous = level;
        }
    }
}
