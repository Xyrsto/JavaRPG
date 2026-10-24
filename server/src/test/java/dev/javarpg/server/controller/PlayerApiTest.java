package dev.javarpg.server.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.server.support.TestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlayerApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
    }

    @Test
    void createdPlayerHasTheStartingShape() throws Exception {
        String body = TestSupport.body(TestSupport.postEmpty(mvc, "/players")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.xp").value(0))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.levelFloorXp").value(0))
                .andExpect(jsonPath("$.nextLevelXp").value(230))
                .andExpect(jsonPath("$.completedQuestIds.length()").value(0))
                .andExpect(jsonPath("$.hero.x").value(5))
                .andExpect(jsonPath("$.hero.y").value(4))
                .andExpect(jsonPath("$.openGates.length()").value(0))
                .andExpect(jsonPath("$.worldEffects.length()").value(0)));
        JsonNode node = TestSupport.parse(body);
        UUID.fromString(node.get("playerId").asText());
    }

    @Test
    void unknownPlayerIsNotFound() throws Exception {
        TestSupport.getJson(mvc, "/players/" + TestSupport.NIL_PLAYER)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PLAYER_NOT_FOUND"));
    }

    @Test
    void malformedPlayerIdIsABadRequest() throws Exception {
        TestSupport.getJson(mvc, "/players/not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void progressSurvivesANewRequest() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.completeV01(mvc, executor, playerId);
        TestSupport.getJson(mvc, "/players/" + playerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xp").value(100))
                .andExpect(jsonPath("$.completedQuestIds[0]").value("V01"))
                .andExpect(jsonPath("$.openGates[0]").value("A"));
    }
}
