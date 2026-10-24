package dev.javarpg.server.docs;

import static dev.javarpg.server.support.TestSupport.API;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.server.exception.ErrorStatus;
import dev.javarpg.server.support.TestSupport;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * FR-003: the documented error codes are exactly the ErrorCode enum, each with the status it
 * really answers. Codes this test cannot reach through the API must be listed with the test that covers them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorCodesDocumentedTest {


    /** Reachable only with a special server state; the named existing test covers each. */
    private static final Map<String, String> COVERED_ELSEWHERE = Map.of(
            "QUEST_PROFILE_UNSUPPORTED", "NonBasicQuestApiTest",
            "RUN_IN_PROGRESS", "RunGateWaitTest",
            "SERVER_BUSY", "RunGateWaitTest",
            "INTERNAL", "ErrorEnvelopeTest",
            "NO_MORE_HINTS", "HintApiTest",
            "FORBIDDEN", "HostHeaderFilterTest",
            "RATE_LIMITED", "RateLimitFilterTest");

    @Autowired
    private MockMvc mvc;

    private JsonNode codeTable() throws Exception {
        JsonNode table = OpenApiFixture.spec(mvc).at("/components/schemas/ErrorBody/x-error-codes");
        assertTrue(table.isObject(), "ErrorBody must carry x-error-codes (code -> status, meaning)");
        return table;
    }

    @Test
    void theDocumentedCodesAreExactlyTheEnum() throws Exception {
        JsonNode spec = OpenApiFixture.spec(mvc);
        Set<String> enumValues = new TreeSet<>();
        spec.at("/components/schemas/ErrorBody/properties/code/enum").forEach(n -> enumValues.add(n.asText()));
        Set<String> expected = new TreeSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            expected.add(code.name());
        }
        assertEquals(expected, enumValues);
        Set<String> tabled = new TreeSet<>();
        codeTable().fieldNames().forEachRemaining(tabled::add);
        assertEquals(expected, tabled, "x-error-codes must list every code");
    }

    @Test
    void theDocumentedStatusOfEveryCodeIsTheOneTheServerUses() throws Exception {
        JsonNode table = codeTable();
        for (ErrorCode code : ErrorCode.values()) {
            HttpStatus status = ErrorStatus.of(code);
            assertEquals(status.value(), table.get(code.name()).get("status").asInt(), code.name());
        }
        table.forEach(entry -> assertTrue(entry.path("meaning").asText().length() > 15, "each code needs a plain meaning"));
    }

    @Test
    void everyCodeThatCanBeReachedIsProducedWithItsDocumentedStatus() throws Exception {
        JsonNode table = codeTable();
        String player = TestSupport.createPlayer(mvc);
        String base = API + "/players/" + player;
        Map<String, MockHttpServletResponse> seen = new TreeMap<>();
        seen.put("BAD_REQUEST", get(API + "/players/not-a-uuid"));
        seen.put("PLAYER_NOT_FOUND", get(API + "/players/" + TestSupport.NIL_PLAYER));
        seen.put("QUEST_NOT_FOUND", get(base + "/quests/V03"));
        seen.put("QUEST_LOCKED", get(base + "/quests/V02"));
        seen.put("HINT_NOT_AVAILABLE", post(base + "/quests/V01/hints/next"));
        seen.put("NO_NPC_ADJACENT", post(base + "/interact"));
        seen.put("NOT_FOUND", get("/no/such/address"));
        MockHttpServletResponse tooLarge = mvc.perform(MockMvcRequestBuilders.post(base + "/quests/V01/run")
                .contentType("application/json").content(TestSupport.sourceBody("x".repeat(30000)))).andReturn().getResponse();
        seen.put("SOURCE_TOO_LARGE", tooLarge);
        for (var entry : seen.entrySet()) {
            JsonNode body = TestSupport.parse(entry.getValue().getContentAsString());
            assertEquals(entry.getKey(), body.at("/error/code").asText(), entry.getKey());
            assertEquals(table.get(entry.getKey()).get("status").asInt(), entry.getValue().getStatus(), entry.getKey());
        }
        Set<String> accountedFor = new TreeSet<>(seen.keySet());
        accountedFor.addAll(COVERED_ELSEWHERE.keySet());
        assertEquals(tableNames(table), accountedFor,
                "a new error code needs either a reachable scenario here or an entry in COVERED_ELSEWHERE");
    }

    private static Set<String> tableNames(JsonNode table) {
        Set<String> names = new TreeSet<>();
        table.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private MockHttpServletResponse get(String uri) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(uri)).andReturn().getResponse();
    }

    private MockHttpServletResponse post(String uri) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(uri)).andReturn().getResponse();
    }
}
