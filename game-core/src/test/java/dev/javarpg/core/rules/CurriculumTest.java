package dev.javarpg.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.Profile;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurriculumTest {

    @Test
    void sevenRegionsInCurriculumOrder() {
        List<String> codes = Curriculum.regions().stream().map(Curriculum.Region::code).toList();
        assertEquals(List.of("V", "F", "G", "L", "D", "C", "A"), codes);
    }

    @Test
    void questCountsSumTo74() {
        int total = 0;
        for (Curriculum.Region region : Curriculum.regions()) {
            total += region.questCount();
        }
        assertEquals(74, total);
    }

    @Test
    void levelRangesAreContiguousFrom1To40() {
        int expectedMin = 1;
        for (Curriculum.Region region : Curriculum.regions()) {
            assertEquals(expectedMin, region.levelMin(), region.code());
            assertTrue(region.levelMax() >= region.levelMin(), region.code());
            expectedMin = region.levelMax() + 1;
        }
        assertEquals(41, expectedMin);
    }

    @Test
    void validQuestIds() {
        assertTrue(Curriculum.isValidQuestId("V01"));
        assertTrue(Curriculum.isValidQuestId("A12"));
        assertFalse(Curriculum.isValidQuestId("V00"));
        assertFalse(Curriculum.isValidQuestId("V11"));
        assertFalse(Curriculum.isValidQuestId("L13"));
        assertFalse(Curriculum.isValidQuestId("X01"));
        assertFalse(Curriculum.isValidQuestId("v01"));
        assertFalse(Curriculum.isValidQuestId(null));
    }

    @Test
    void globalIndexFollowsRegionOrder() {
        assertEquals(0, Curriculum.globalIndex("V01"));
        assertEquals(10, Curriculum.globalIndex("F01"));
        assertEquals(73, Curriculum.globalIndex("A12"));
    }

    @Test
    void globalIndexRejectsInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> Curriculum.globalIndex("V00"));
    }

    @Test
    void bossFlags() {
        assertTrue(Curriculum.isBoss("V10"));
        assertTrue(Curriculum.isBoss("L12"));
        assertFalse(Curriculum.isBoss("V09"));
    }

    @Test
    void expectedProfilePerQuest() {
        assertEquals(Profile.BASIC, Curriculum.expectedProfile("V01"));
        assertEquals(Profile.BASIC, Curriculum.expectedProfile("L12"));
        assertEquals(Profile.BASIC, Curriculum.expectedProfile("A01"));
        assertEquals(Profile.BASIC, Curriculum.expectedProfile("A02"));
        assertEquals(Profile.TESTING, Curriculum.expectedProfile("D01"));
        assertEquals(Profile.BUILD, Curriculum.expectedProfile("D07"));
        assertEquals(Profile.BUILD, Curriculum.expectedProfile("D09"));
        assertEquals(Profile.FILES, Curriculum.expectedProfile("D05"));
        assertEquals(Profile.TESTING, Curriculum.expectedProfile("D10"));
        assertEquals(Profile.THREADS, Curriculum.expectedProfile("C03"));
        assertEquals(Profile.SERVICE, Curriculum.expectedProfile("A03"));
        assertEquals(Profile.SERVICE, Curriculum.expectedProfile("A12"));
    }
}
