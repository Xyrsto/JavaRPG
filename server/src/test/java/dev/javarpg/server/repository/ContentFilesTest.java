package dev.javarpg.server.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.ChallengeKind;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.runner.guard.SourceGuard;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the shipped content in content/ (the real quest and world files). */
class ContentFilesTest {

    private static final WorldDef WORLD = new WorldLoader().load(Path.of("../content/world/village.json"));
    private static final FileQuestRepository REPO = new FileQuestRepository(Path.of("../content/quests"), WORLD);

    @Test
    void exactlyV01AndV02AreShipped() {
        List<Quest> quests = REPO.findAllOrdered();
        assertEquals(2, quests.size());
        assertEquals("V01", quests.get(0).id());
        assertEquals("V02", quests.get(1).id());
        for (Quest quest : quests) {
            assertEquals(Profile.BASIC, quest.profile());
            assertEquals(ChallengeKind.STDOUT, quest.kind());
        }
    }

    @Test
    void titlesAndConceptsMatchTheCurriculum() {
        Quest v01 = REPO.findById("V01").orElseThrow();
        assertEquals("The Adventurer's Name", v01.title());
        assertEquals("Variables and String", v01.concept());
        Quest v02 = REPO.findById("V02").orElseThrow();
        assertEquals("The Four Elements", v02.title());
        assertEquals("Primitive types", v02.concept());
    }

    @Test
    void worldAndQuestsAgree() {
        assertTrue(WORLD.map().rows().stream().anyMatch(row -> row.contains("A")), "gate A missing");
        assertTrue(WORLD.map().rows().stream().anyMatch(row -> row.contains("B")), "gate B missing");
        assertEquals("A", REPO.findById("V01").orElseThrow().worldEffect().target());
        assertEquals("B", REPO.findById("V02").orElseThrow().worldEffect().target());
        assertEquals("OPEN_GATE", REPO.findById("V01").orElseThrow().worldEffect().type());
    }

    @Test
    void referenceSolutionsPassTheSourceGuard() {
        for (Quest quest : REPO.findAllOrdered()) {
            assertTrue(SourceGuard.check(quest.referenceSolution(), Profile.BASIC).allowed(),
                    quest.id() + " reference solution is rejected by the guard");
        }
    }

    @Test
    void starterCodeHasTodosAndReferenceSolutionDoesNot() {
        for (Quest quest : REPO.findAllOrdered()) {
            assertTrue(quest.starterCode().contains("public class Main"), quest.id());
            assertTrue(quest.starterCode().contains("// TODO"), quest.id());
            assertTrue(quest.referenceSolution().contains("public class Main"), quest.id());
            assertTrue(!quest.referenceSolution().contains("TODO"), quest.id());
        }
    }
}
