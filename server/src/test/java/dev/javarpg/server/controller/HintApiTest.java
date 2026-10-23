package dev.javarpg.server.controller;

import static dev.javarpg.server.support.TestSupport.V01_SOLUTION;
import static dev.javarpg.server.support.TestSupport.ok;
import static dev.javarpg.server.support.TestSupport.program;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.server.support.TestSupport;
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
class HintApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
    }

    @Test
    void aHintBeforeAnyAttemptIsNotAvailable() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.hint(mvc, playerId, "V01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HINT_NOT_AVAILABLE"));
    }

    @Test
    void hintsUnlockInOrderAfterFailedRunsAndRunOutAtThree() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        program(executor, request -> ok("wrong\n"));
        TestSupport.run(mvc, playerId, "V01", V01_SOLUTION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false));

        for (int n = 1; n <= 3; n++) {
            TestSupport.hint(mvc, playerId, "V01")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hintNumber").value(n))
                    .andExpect(jsonPath("$.hintsUnlocked").value(n))
                    .andExpect(jsonPath("$.hintsTotal").value(3))
                    .andExpect(jsonPath("$.hint").isNotEmpty());
        }
        TestSupport.hint(mvc, playerId, "V01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_MORE_HINTS"));
    }

    @Test
    void theDetailShowsOnlyUnlockedHints() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        program(executor, request -> ok("wrong\n"));
        TestSupport.run(mvc, playerId, "V01", V01_SOLUTION).andExpect(status().isOk());
        TestSupport.getJson(mvc, "/players/" + playerId + "/quests/V01")
                .andExpect(jsonPath("$.hints.length()").value(0))
                .andExpect(jsonPath("$.hintsUnlocked").value(0));

        String hintBody = TestSupport.body(TestSupport.hint(mvc, playerId, "V01").andExpect(status().isOk()));
        String firstHint = TestSupport.parse(hintBody).get("hint").asText();

        String detailBody = TestSupport.body(TestSupport.getJson(mvc, "/players/" + playerId + "/quests/V01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hints.length()").value(1))
                .andExpect(jsonPath("$.hintsUnlocked").value(1)));
        JsonNode hints = TestSupport.parse(detailBody).get("hints");
        assertEquals(firstHint, hints.get(0).asText());
    }

    @Test
    void hintsAreAvailableAfterCompletionWithoutAFailure() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.completeV01(mvc, executor, playerId);
        TestSupport.hint(mvc, playerId, "V01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintNumber").value(1));
        assertTrue(true);
    }
}
