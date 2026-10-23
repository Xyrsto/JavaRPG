package dev.javarpg.server.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class QuestApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
    }

    @Test
    void listShowsV01AvailableAndV02Locked() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.getJson(mvc, "/players/" + playerId + "/quests")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quests[0].id").value("V01"))
                .andExpect(jsonPath("$.quests[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.quests[0].title").value("The Adventurer's Name"))
                .andExpect(jsonPath("$.quests[0].xp").value(100))
                .andExpect(jsonPath("$.quests[1].id").value("V02"))
                .andExpect(jsonPath("$.quests[1].status").value("LOCKED"));
    }

    @Test
    void lockedQuestDetailIsForbidden() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.getJson(mvc, "/players/" + playerId + "/quests/V02")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("QUEST_LOCKED"));
    }

    @Test
    void detailBeforeCompletionNeverContainsSecrets() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        ResultActions result = TestSupport.getJson(mvc, "/players/" + playerId + "/quests/V01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.starterCode").isNotEmpty())
                .andExpect(jsonPath("$.hintsTotal").value(3))
                .andExpect(jsonPath("$.hintsUnlocked").value(0))
                .andExpect(jsonPath("$.hints.length()").value(0))
                .andExpect(jsonPath("$.visibleTests[0].expected").value("Hello, Adventurer!"));
        String body = TestSupport.body(result);
        assertFalse(body.contains("String name = "), "reference solution leaked");
        assertFalse(body.contains("hiddenTests"));
        assertFalse(body.contains("exact output"));
        assertFalse(body.contains("prints the variable"));
        assertTrue(TestSupport.parse(body).get("referenceSolution").isNull());
        assertTrue(TestSupport.parse(body).get("explanation").isNull());
    }

    @Test
    void detailAfterCompletionShowsSolutionAndExplanation() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.completeV01(mvc, executor, playerId);
        TestSupport.getJson(mvc, "/players/" + playerId + "/quests/V01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.worldEffect.target").value("A"));
    }

    @Test
    void lastSourceIsRestoredAfterARun() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.program(executor, request -> TestSupport.ok(""));
        String source = TestSupport.lines("public class Main {", "    // my first attempt", "}");
        TestSupport.run(mvc, playerId, "V01", source).andExpect(status().isOk());
        TestSupport.getJson(mvc, "/players/" + playerId + "/quests/V01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastSource").value(source));
    }

    @Test
    void unknownButWellFormedQuestIsNotFound() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.getJson(mvc, "/players/" + playerId + "/quests/V05")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("QUEST_NOT_FOUND"));
    }

    @Test
    void malformedQuestIdIsABadRequest() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.getJson(mvc, "/players/" + playerId + "/quests/x1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
