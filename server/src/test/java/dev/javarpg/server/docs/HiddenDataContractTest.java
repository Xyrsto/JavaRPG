package dev.javarpg.server.docs;

import static dev.javarpg.server.support.TestSupport.V01_OUTPUT;
import static dev.javarpg.server.support.TestSupport.V01_SOLUTION;
import static dev.javarpg.server.support.TestSupport.ok;
import static dev.javarpg.server.support.TestSupport.program;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.server.support.TestSupport;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-005, FR-008: the fields the description marks as withheld are really absent before completion and present
 * after it; for hidden tests the expected and actual values are never filled. The markers in the description
 * drive the assertions, so removing a marker also fails here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HiddenDataContractTest {

    private static final String WITHHELD = "x-withheld-until-completed";
    private static final String NEVER_FOR_HIDDEN = "x-never-shown-for-hidden-tests";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
    }

    private Set<String> marked(JsonNode spec, String marker) {
        Set<String> found = new TreeSet<>();
        spec.at("/components/schemas").fields().forEachRemaining(schema ->
                schema.getValue().path("properties").fields().forEachRemaining(property -> {
                    if (property.getValue().path(marker).asBoolean(false)) {
                        found.add(schema.getKey() + "." + property.getKey());
                    }
                }));
        return found;
    }

    @Test
    void theDescriptionMarksTheFieldsThatAreWithheld() throws Exception {
        JsonNode spec = OpenApiFixture.spec(mvc);
        assertEquals(Set.of("QuestDetailView.explanation", "QuestDetailView.referenceSolution", "QuestDetailView.worldEffect"),
                marked(spec, WITHHELD));
        assertEquals(Set.of("TestResultView.actual", "TestResultView.expected"), marked(spec, NEVER_FOR_HIDDEN));
    }

    @Test
    void anIncompleteQuestNeverShowsWithheldFields() throws Exception {
        String player = TestSupport.createPlayer(mvc);
        String quest = "/players/" + player + "/quests/V01";
        assertWithheldAbsent(TestSupport.parse(TestSupport.body(TestSupport.getJson(mvc, quest))));
        program(executor, request -> ok("something else\n"));
        JsonNode run = TestSupport.parse(TestSupport.body(TestSupport.run(mvc, player, "V01", V01_SOLUTION)));
        JsonNode submit = TestSupport.parse(TestSupport.body(TestSupport.submit(mvc, player, "V01", V01_SOLUTION)));
        assertHiddenResultsAreBlank(run, false);
        assertHiddenResultsAreBlank(submit, true);
        assertTrue(submit.get("completion").isNull(), "a failed submit has no completion block");
        assertWithheldAbsent(TestSupport.parse(TestSupport.body(TestSupport.getJson(mvc, quest))));
    }

    @Test
    void aCompletedQuestShowsTheWithheldFields() throws Exception {
        String player = TestSupport.createPlayer(mvc);
        program(executor, request -> ok(V01_OUTPUT));
        JsonNode submit = TestSupport.parse(TestSupport.body(TestSupport.submit(mvc, player, "V01", V01_SOLUTION)));
        assertTrue(submit.get("passed").asBoolean());
        assertHiddenResultsAreBlank(submit, true);
        JsonNode detail = TestSupport.parse(TestSupport.body(TestSupport.getJson(mvc, "/players/" + player + "/quests/V01")));
        assertEquals("COMPLETED", detail.get("status").asText());
        assertFalse(detail.path("explanation").asText("").isBlank());
        assertFalse(detail.path("referenceSolution").asText("").isBlank());
        assertFalse(detail.path("worldEffect").isNull());
    }

    private void assertWithheldAbsent(JsonNode detail) {
        for (String field : new String[] {"explanation", "referenceSolution", "worldEffect"}) {
            assertTrue(detail.path(field).isMissingNode() || detail.get(field).isNull(), field + " must be withheld");
        }
    }

    private void assertHiddenResultsAreBlank(JsonNode result, boolean requireHidden) {
        boolean sawHidden = false;
        for (JsonNode test : result.get("tests")) {
            if (test.get("hidden").asBoolean()) {
                sawHidden = true;
                assertTrue(test.path("expected").isNull() || test.path("expected").isMissingNode(), "hidden expected");
                assertTrue(test.path("actual").isNull() || test.path("actual").isMissingNode(), "hidden actual");
            }
        }
        assertTrue(sawHidden || !requireHidden, "a submit result should contain hidden tests");
    }
}
