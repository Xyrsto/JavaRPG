package dev.javarpg.server.controller;

import static dev.javarpg.server.support.TestSupport.V01_OUTPUT;
import static dev.javarpg.server.support.TestSupport.V01_SOLUTION;
import static dev.javarpg.server.support.TestSupport.V02_SOLUTION;
import static dev.javarpg.server.support.TestSupport.lines;
import static dev.javarpg.server.support.TestSupport.ok;
import static dev.javarpg.server.support.TestSupport.program;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.server.support.TestSupport;
import java.util.concurrent.atomic.AtomicInteger;
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
class SubmitFlowTest {

    private static final String JAVAC_OUTPUT = lines(
            "Main.java:3: error: ';' expected",
            "        System.out.println(\"Hello, Adventurer!\")",
            "                                                  ^",
            "1 error");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
    }

    @Test
    void successOpensTheGateAndAwardsXp() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        program(executor, request -> ok(V01_OUTPUT));
        TestSupport.submit(mvc, playerId, "V01", V01_SOLUTION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.scope").value("ALL"))
                .andExpect(jsonPath("$.summary").value("Challenge complete!"))
                .andExpect(jsonPath("$.completion.firstCompletion").value(true))
                .andExpect(jsonPath("$.completion.xpAwarded").value(100))
                .andExpect(jsonPath("$.completion.totalXp").value(100))
                .andExpect(jsonPath("$.completion.worldEffect.type").value("OPEN_GATE"))
                .andExpect(jsonPath("$.completion.worldEffect.target").value("A"))
                .andExpect(jsonPath("$.completion.unlockedQuestId").value("V02"));
        TestSupport.getJson(mvc, "/players/" + playerId)
                .andExpect(jsonPath("$.openGates[0]").value("A"));
    }

    @Test
    void resubmittingAwardsNoMoreXp() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.completeV01(mvc, executor, playerId);
        program(executor, request -> ok(V01_OUTPUT));
        TestSupport.submit(mvc, playerId, "V01", V01_SOLUTION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.completion.firstCompletion").value(false))
                .andExpect(jsonPath("$.completion.xpAwarded").value(0));
    }

    @Test
    void compileErrorKeepsTheProgressAndTheSource() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        String broken = lines(
                "public class Main {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Hello, Adventurer!\")",
                "    }",
                "}");
        program(executor, request -> TestSupport.compileError(JAVAC_OUTPUT));
        TestSupport.submit(mvc, playerId, "V01", broken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_ERROR"))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.compilerOutput").value(JAVAC_OUTPUT))
                .andExpect(jsonPath("$.friendlyHints.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        TestSupport.getJson(mvc, "/players/" + playerId)
                .andExpect(jsonPath("$.xp").value(0));
        TestSupport.getJson(mvc, "/players/" + playerId + "/quests/V01")
                .andExpect(jsonPath("$.failedAttempts").value(1))
                .andExpect(jsonPath("$.lastSource").value(broken));

        program(executor, request -> ok(V01_OUTPUT));
        TestSupport.submit(mvc, playerId, "V01", V01_SOLUTION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));
    }

    @Test
    void timeoutIsReportedInFriendlyWords() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        program(executor, request -> TestSupport.timeout());
        TestSupport.submit(mvc, playerId, "V01", V01_SOLUTION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TIMEOUT"))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.summary").isNotEmpty());
    }

    @Test
    void runNeverCompletesTheQuest() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        program(executor, request -> ok(V01_OUTPUT));
        TestSupport.run(mvc, playerId, "V01", V01_SOLUTION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("VISIBLE"))
                .andExpect(jsonPath("$.passed").value(true));
        TestSupport.getJson(mvc, "/players/" + playerId + "/quests")
                .andExpect(jsonPath("$.quests[0].status").value("AVAILABLE"));
        TestSupport.getJson(mvc, "/players/" + playerId)
                .andExpect(jsonPath("$.xp").value(0));
    }

    @Test
    void sandboxErrorIsNotCountedAsAFailedAttempt() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        program(executor, request -> TestSupport.sandboxError("docker is not running"));
        TestSupport.submit(mvc, playerId, "V01", V01_SOLUTION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SANDBOX_ERROR"))
                .andExpect(jsonPath("$.passed").value(false));
        TestSupport.getJson(mvc, "/players/" + playerId + "/quests/V01")
                .andExpect(jsonPath("$.failedAttempts").value(0));
    }

    @Test
    void oversizedSourceIsRejectedBeforeAnyRun() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        AtomicInteger calls = new AtomicInteger();
        program(executor, request -> {
            calls.incrementAndGet();
            return ok("");
        });
        String huge = "x".repeat(20001);
        TestSupport.submit(mvc, playerId, "V01", huge)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("SOURCE_TOO_LARGE"));
        assertEquals(0, calls.get());
    }

    @Test
    void guardRejectedSourceIsAFailedReportNotAnError() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        AtomicInteger calls = new AtomicInteger();
        program(executor, request -> {
            calls.incrementAndGet();
            return ok("");
        });
        String exiting = lines(
                "public class Main {",
                "    public static void main(String[] args) {",
                "        System.exit(0);",
                "    }",
                "}");
        TestSupport.submit(mvc, playerId, "V01", exiting)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false));
        assertEquals(0, calls.get());
    }

    @Test
    void lockedQuestCannotBeSubmitted() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.submit(mvc, playerId, "V02", V02_SOLUTION)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("QUEST_LOCKED"));
    }

    @Test
    void hiddenTestsAreOpaqueWhenTheyFail() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        program(executor, request -> ok("Hi\n"));
        String body = TestSupport.body(TestSupport.submit(mvc, playerId, "V01", V01_SOLUTION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false)));
        JsonNode tests = TestSupport.parse(body).get("tests");
        int hidden = 0;
        for (JsonNode test : tests) {
            if (test.get("hidden").asBoolean()) {
                hidden++;
                assertTrue(test.get("name").asText().startsWith("Hidden test "));
                assertTrue(test.get("expected").isNull());
                assertTrue(test.get("actual").isNull());
                assertFalse(test.get("message").asText().isEmpty());
            }
        }
        assertTrue(hidden > 0, "expected at least one hidden test in the Submit result");
    }
}
