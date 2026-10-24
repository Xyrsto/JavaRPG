package dev.javarpg.server.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.server.support.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorldApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
    }

    @Test
    void worldViewHasTheMapGatesAndNpcsWithoutDialogue() throws Exception {
        ResultActions result = TestSupport.getJson(mvc, "/world");
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("village"))
                .andExpect(jsonPath("$.width").value(12))
                .andExpect(jsonPath("$.height").value(9))
                .andExpect(jsonPath("$.rows.length()").value(9))
                .andExpect(jsonPath("$.heroStart.x").value(5))
                .andExpect(jsonPath("$.heroStart.y").value(4))
                .andExpect(jsonPath("$.gates[0].id").value("A"))
                .andExpect(jsonPath("$.gates[0].x").value(5))
                .andExpect(jsonPath("$.gates[0].y").value(6))
                .andExpect(jsonPath("$.gates[1].id").value("B"))
                .andExpect(jsonPath("$.gates[1].x").value(11))
                .andExpect(jsonPath("$.gates[1].y").value(7))
                .andExpect(jsonPath("$.npcs[0].questId").value("V01"))
                .andExpect(jsonPath("$.legend['#']").value("wall"));
        assertFalse(TestSupport.body(result).contains("introLines"));
    }

    @Test
    void movingWalksOneTilePerDirection() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.move(mvc, playerId, "UP", "UP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.x").value(5))
                .andExpect(jsonPath("$.y").value(2))
                .andExpect(jsonPath("$.applied").value(2))
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void aWallStopsTheMoveAndIsReported() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.move(mvc, playerId, "LEFT", "LEFT", "LEFT", "LEFT", "LEFT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.x").value(1))
                .andExpect(jsonPath("$.y").value(4))
                .andExpect(jsonPath("$.applied").value(4))
                .andExpect(jsonPath("$.blocked").value(true));
    }

    @Test
    void anEmptyDirectionListIsABadRequest() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.postJson(mvc, "/players/" + playerId + "/move", "{\"directions\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void tooManyDirectionsIsABadRequest() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        String[] thirtyThree = new String[33];
        java.util.Arrays.fill(thirtyThree, "UP");
        TestSupport.move(mvc, playerId, thirtyThree)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void anUnknownDirectionIsABadRequest() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.move(mvc, playerId, "SIDEWAYS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void aClosedGateBlocksMovement() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.move(mvc, playerId, "DOWN", "DOWN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.y").value(5))
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.blocked").value(true));
    }

    @Test
    void theGateOpensAfterV01IsCompleted() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.move(mvc, playerId, "DOWN").andExpect(status().isOk());
        TestSupport.completeV01(mvc, executor, playerId);
        TestSupport.move(mvc, playerId, "DOWN", "DOWN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.y").value(7))
                .andExpect(jsonPath("$.applied").value(2))
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void interactingNearElderMiraReturnsDialogueAndQuestStatus() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.move(mvc, playerId, "LEFT", "LEFT", "UP").andExpect(status().isOk());
        TestSupport.interact(mvc, playerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.npcId").value("elder"))
                .andExpect(jsonPath("$.npcName").value("Elder Mira"))
                .andExpect(jsonPath("$.questId").value("V01"))
                .andExpect(jsonPath("$.questStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.lines[0]").value("Traveller! The north gate is sealed by an old spell."));
    }

    @Test
    void interactingFarFromAnyNpcIsAConflict() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.interact(mvc, playerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_NPC_ADJACENT"));
    }

    @Test
    void interactingAfterCompletionReturnsTheCompletedLines() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.move(mvc, playerId, "LEFT", "LEFT", "UP").andExpect(status().isOk());
        TestSupport.completeV01(mvc, executor, playerId);
        TestSupport.interact(mvc, playerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.lines[0]").value("The gate is open. You have a way with words, hero."));
    }
}
