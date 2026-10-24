package dev.javarpg.server.controller;

import static dev.javarpg.server.support.TestSupport.V01_SOLUTION;
import static dev.javarpg.server.support.TestSupport.program;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.server.support.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Characterization test (spec 005): the exact status, content type and message of every failure that the
 * exception handler produces. Written against the code before error codes were unified; it must stay green.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandlerErrorBodiesUnchangedTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
    }

    private static void expect(MockHttpServletResponse response, int status, String code, String message) throws Exception {
        String body = response.getContentAsString();
        assertEquals(status, response.getStatus(), body);
        assertTrue(String.valueOf(response.getContentType()).startsWith("application/json"), response.getContentType());
        assertEquals(TestSupport.parse("{\"error\":{\"code\":" + TestSupport.quote(code) + ",\"message\":"
                + TestSupport.quote(message) + "}}"), TestSupport.parse(body), body);
        assertEquals(1, TestSupport.parse(body).size(), "only the error object");
        assertEquals(2, TestSupport.parse(body).get("error").size(), "only code and message");
    }

    private MockHttpServletResponse get(String uri) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(uri)).andReturn().getResponse();
    }

    @Test
    void everyFailureOfTheExceptionHandlerKeepsItsCodeStatusAndMessage() throws Exception {
        String player = TestSupport.createPlayer(mvc);
        String base = TestSupport.API + "/players/" + player;

        expect(get(TestSupport.API + "/players/not-a-uuid"), 400, "BAD_REQUEST", "That player id is not valid.");
        expect(get(TestSupport.API + "/players/" + TestSupport.NIL_PLAYER), 404, "PLAYER_NOT_FOUND",
                "We could not find that player. Start a new game.");
        expect(get(base + "/quests/V03"), 404, "QUEST_NOT_FOUND", "There is no quest with that id.");
        expect(get(base + "/quests/V02"), 403, "QUEST_LOCKED", "Finish \"The Adventurer's Name\" first.");
        expect(mvc.perform(MockMvcRequestBuilders.post(base + "/quests/V01/hints/next")).andReturn().getResponse(),
                409, "HINT_NOT_AVAILABLE", "Give the challenge a try first, then ask for a hint.");
        expect(mvc.perform(MockMvcRequestBuilders.post(base + "/interact")).andReturn().getResponse(),
                409, "NO_NPC_ADJACENT", "There is nobody to talk to here.");
        expect(TestSupport.run(mvc, player, "V01", "x".repeat(30000)).andReturn().getResponse(),
                413, "SOURCE_TOO_LARGE", "That code is too long. Keep it under 20000 characters.");
        expect(get("/no/such/address"), 404, "NOT_FOUND", "There is no such address.");
        expect(get(base + "/quests/V01/run"), 405, "BAD_REQUEST", "That action is not supported here.");
        expect(mvc.perform(MockMvcRequestBuilders.post(base + "/quests/V01/run").contentType("text/plain").content("hi"))
                .andReturn().getResponse(), 415, "BAD_REQUEST", "Send your request as JSON.");
        expect(TestSupport.postJson(mvc, "/players/" + player + "/move", "{not json").andReturn().getResponse(),
                400, "BAD_REQUEST", "Something in your request was not understood. Check the values and try again.");
        expect(TestSupport.move(mvc, player, "SIDEWAYS").andReturn().getResponse(), 400, "BAD_REQUEST",
                "Unknown direction. Use UP, DOWN, LEFT or RIGHT.");
    }

    @Test
    void anUnexpectedFailureKeepsTheGenericInternalError() throws Exception {
        String player = TestSupport.createPlayer(mvc);
        program(executor, request -> {
            throw new IllegalStateException("secret internal detail");
        });
        expect(TestSupport.submit(mvc, player, "V01", V01_SOLUTION).andReturn().getResponse(), 500, "INTERNAL",
                "Something went wrong on our side. Your progress is safe.");
    }
}
