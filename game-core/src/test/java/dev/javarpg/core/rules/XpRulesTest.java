package dev.javarpg.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XpRulesTest {

    @Test
    void normalQuestsGive100Xp() {
        assertEquals(100, XpRules.xpFor("V01"));
        assertEquals(100, XpRules.xpFor("L11"));
    }

    @Test
    void bossQuestsGive250Xp() {
        assertEquals(250, XpRules.xpFor("V10"));
        assertEquals(250, XpRules.xpFor("L12"));
        assertEquals(250, XpRules.xpFor("A12"));
    }

    @Test
    void xpFloorForLevelFollowsRegionFormula() {
        assertEquals(0, XpRules.xpFloorForLevel(1));
        assertEquals(230, XpRules.xpFloorForLevel(2));
        assertEquals(920, XpRules.xpFloorForLevel(5));
        assertEquals(1150, XpRules.xpFloorForLevel(6));
        assertEquals(4575, XpRules.xpFloorForLevel(21));
        assertEquals(4800, XpRules.xpFloorForLevel(22));
        assertEquals(7100, XpRules.xpFloorForLevel(34));
        assertEquals(8258, XpRules.xpFloorForLevel(40));
    }
}
