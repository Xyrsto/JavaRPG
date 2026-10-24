package dev.javarpg.server.controller;

import static dev.javarpg.server.support.TestSupport.V01_SOLUTION;
import static dev.javarpg.server.support.TestSupport.V02_SOLUTION;
import static dev.javarpg.server.support.TestSupport.program;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorEnvelopeTest {

    private static final String GENERIC_INTERNAL = "Something went wrong on our side. Your progress is safe.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
    }

    @Test
    void everyReachableErrorUsesTheEnvelopeAndTheListedStatus() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        String huge = "x".repeat(20001);

        expectError(TestSupport.postJson(mvc, "/players/" + playerId + "/move", "{not json"), 400, "BAD_REQUEST");
        expectError(TestSupport.getJson(mvc, "/players/not-a-uuid"), 400, "BAD_REQUEST");
        expectError(TestSupport.getJson(mvc, "/players/" + TestSupport.NIL_PLAYER), 404, "PLAYER_NOT_FOUND");
        expectError(TestSupport.getJson(mvc, "/players/" + playerId + "/quests/V05"), 404, "QUEST_NOT_FOUND");
        expectError(TestSupport.submit(mvc, playerId, "V02", V02_SOLUTION), 403, "QUEST_LOCKED");
        expectError(TestSupport.submit(mvc, playerId, "V01", huge), 413, "SOURCE_TOO_LARGE");
        expectError(TestSupport.interact(mvc, playerId), 409, "NO_NPC_ADJACENT");
        expectError(TestSupport.hint(mvc, playerId, "V01"), 409, "HINT_NOT_AVAILABLE");
        expectError(TestSupport.move(mvc, playerId, "SIDEWAYS"), 400, "BAD_REQUEST");
    }

    @Test
    void anUnexpectedFailureBecomesAGenericInternalError() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        program(executor, request -> {
            throw new IllegalStateException("secret internal detail");
        });
        ResultActions result = TestSupport.submit(mvc, playerId, "V01", V01_SOLUTION);
        expectError(result, 500, "INTERNAL");
        String body = TestSupport.body(result);
        assertEquals(GENERIC_INTERNAL, TestSupport.parse(body).get("error").get("message").asText());
        assertFalse(body.contains("secret internal detail"));
        assertFalse(body.contains("at dev.javarpg"));
    }

    private static void expectError(ResultActions result, int status, String code) throws Exception {
        int actual = result.andReturn().getResponse().getStatus();
        String body = TestSupport.body(result);
        assertEquals(status, actual, body);
        JsonNode error = TestSupport.parse(body).get("error");
        assertEquals(code, error.get("code").asText(), body);
        assertFalse(error.get("message").asText().isEmpty(), body);
        assertFalse(body.contains("at dev.javarpg"), body);
    }
}
