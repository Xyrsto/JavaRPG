package dev.javarpg.server.controller;

import static dev.javarpg.server.support.TestSupport.body;
import static dev.javarpg.server.support.TestSupport.ok;
import static dev.javarpg.server.support.TestSupport.program;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.server.support.TestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T251 / NFR-001: hidden test data never leaves the server. The raw JSON of run, submit, hint, quest detail
 * and quest list must not contain hidden expected values, hidden test names, the reference solution or the
 * explanation before the quest is completed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HiddenDataLeakTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WRONG_SOURCE = "public class Main { public static void main(String[] a) { } }";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
    }

    @Test
    void v01ResponsesNeverLeakHiddenData() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        assertNoLeaks("V01", collect(playerId, "V01"));
    }

    @Test
    void v02ResponsesNeverLeakHiddenData() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.completeV01(mvc, executor, playerId);
        assertNoLeaks("V02", collect(playerId, "V02"));
    }

    /** Calls every endpoint that returns quest data before the quest is completed. */
    private List<String> collect(String playerId, String questId) throws Exception {
        List<String> bodies = new ArrayList<>();
        bodies.add(body(TestSupport.getJson(mvc, "/players/" + playerId + "/quests")));
        bodies.add(body(TestSupport.getJson(mvc, "/players/" + playerId + "/quests/" + questId)));
        program(executor, request -> ok("something else\n"));
        bodies.add(body(TestSupport.run(mvc, playerId, questId, WRONG_SOURCE)));
        bodies.add(body(TestSupport.submit(mvc, playerId, questId, WRONG_SOURCE)));
        program(executor, request -> TestSupport.compileError("Main.java:1: error: boom"));
        bodies.add(body(TestSupport.submit(mvc, playerId, questId, WRONG_SOURCE)));
        bodies.add(body(TestSupport.hint(mvc, playerId, questId)));
        bodies.add(body(TestSupport.getJson(mvc, "/players/" + playerId + "/quests/" + questId)));
        bodies.add(body(TestSupport.getJson(mvc, "/players/" + playerId + "/quests")));
        return bodies;
    }

    private void assertNoLeaks(String questId, List<String> bodies) throws Exception {
        JsonNode quest = JSON.readTree(Files.readString(Path.of("../content/quests/" + questId + ".json")));
        Set<String> visibleExpected = new HashSet<>();
        for (JsonNode t : quest.get("visibleTests")) {
            visibleExpected.add(t.get("expected").asText());
        }
        // Text the player is legitimately shown before completion; reference lines found there are not secrets.
        StringBuilder publicText = new StringBuilder();
        for (String field : new String[] {"title", "concept", "story", "goal"}) {
            publicText.append(quest.get(field).asText()).append('\n');
        }
        for (String field : new String[] {"hints", "starterCode"}) {
            for (JsonNode item : quest.get(field)) {
                publicText.append(item.asText()).append('\n');
            }
        }
        for (JsonNode t : quest.get("visibleTests")) {
            publicText.append(t.get("name").asText()).append('\n').append(t.get("expected").asText()).append('\n');
        }
        for (JsonNode m : quest.get("commonMistakes")) {
            publicText.append(m.get("message").asText()).append('\n');
        }

        List<String> secrets = new ArrayList<>();
        for (JsonNode t : quest.get("hiddenTests")) {
            String expected = t.get("expected").asText();
            if (!visibleExpected.contains(expected)) {
                secrets.add(expected);
            }
            secrets.add(t.get("name").asText());
        }
        List<String> reference = new ArrayList<>();
        for (JsonNode line : quest.get("referenceSolution")) {
            String trimmed = line.asText().trim();
            reference.add(line.asText());
            if (trimmed.length() >= 12 && !publicText.toString().contains(trimmed) && !WRONG_SOURCE.contains(trimmed)) {
                secrets.add(trimmed);
            }
        }
        secrets.add(String.join("\n", reference));
        secrets.add(quest.get("explanation").asText());

        for (String body : bodies) {
            for (String secret : secrets) {
                String escaped = TestSupport.quote(secret);
                escaped = escaped.substring(1, escaped.length() - 1);
                assertFalse(body.contains(secret), questId + " leaked [" + secret + "] in " + body);
                assertFalse(body.contains(escaped), questId + " leaked (escaped) [" + secret + "] in " + body);
            }
        }
        assertTrue(bodies.size() >= 8, "all endpoints were exercised");
    }
}
