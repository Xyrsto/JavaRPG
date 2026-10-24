package dev.javarpg.server.docs;

import static dev.javarpg.server.support.TestSupport.NIL_PLAYER;
import static dev.javarpg.server.support.TestSupport.V01_OUTPUT;
import static dev.javarpg.server.support.TestSupport.V01_SOLUTION;
import static dev.javarpg.server.support.TestSupport.ok;
import static dev.javarpg.server.support.TestSupport.program;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.server.support.TestSupport;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * FR-002, FR-007: real responses (success and failure) conform to the schema the description documents for
 * their operation and status, and every worked example in the description conforms too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResponseConformanceTest {

    private record Exchange(String method, String uri, int status, String body) {
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    private final List<Exchange> exchanges = new ArrayList<>();

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
        exchanges.clear();
    }

    private ResultActions record(String method, String uri, ResultActions result) throws Exception {
        MockHttpServletResponse response = result.andReturn().getResponse();
        exchanges.add(new Exchange(method, uri, response.getStatus(), response.getContentAsString()));
        return result;
    }

    private String get(String uri) throws Exception {
        ResultActions r = record("GET", uri, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(uri)));
        return r.andReturn().getResponse().getContentAsString();
    }

    private String post(String uri, String json) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(uri);
        if (json != null) {
            request.contentType("application/json").content(json);
        }
        ResultActions r = record("POST", uri, mvc.perform(request));
        return r.andReturn().getResponse().getContentAsString();
    }

    private void playThroughTheGame() throws Exception {
        String api = TestSupport.API;
        get("/health");
        get(api + "/world");
        String created = post(api + "/players", null);
        String id = TestSupport.parse(created).get("playerId").asText();
        String player = api + "/players/" + id;
        get(player);
        post(player + "/move", TestSupport.moveBody("LEFT", "LEFT", "UP"));
        post(player + "/move", TestSupport.moveBody("UP", "UP", "UP", "UP", "UP", "UP", "UP", "UP"));
        post(player + "/interact", null);
        get(player + "/quests");
        get(player + "/quests/V01");
        post(player + "/quests/V01/hints/next", null);
        program(executor, request -> ok("wrong\n"));
        post(player + "/quests/V01/run", TestSupport.sourceBody(V01_SOLUTION));
        post(player + "/quests/V01/submit", TestSupport.sourceBody(V01_SOLUTION));
        post(player + "/quests/V01/hints/next", null);
        program(executor, request -> ok(V01_OUTPUT));
        post(player + "/quests/V01/run", TestSupport.sourceBody(V01_SOLUTION));
        post(player + "/quests/V01/submit", TestSupport.sourceBody(V01_SOLUTION));
        get(player + "/quests/V01");
        get(player);
        get(player + "/quests/V02");
        // failures
        get(api + "/players/not-a-uuid");
        get(api + "/players/" + NIL_PLAYER);
        get(player + "/quests/V99");
        get(player + "/quests/Z01");
        post(player + "/move", "{\"directions\":[]}");
        post(player + "/move", "{\"directions\":[\"SIDEWAYS\"]}");
        post(player + "/quests/V01/run", "{not json");
        post(player + "/quests/V01/run", TestSupport.sourceBody("x".repeat(30000)));
        mvcPostWrongContentType(player + "/quests/V01/run");
        mvcMethodNotAllowed(player + "/quests/V01/run");
    }

    private void mvcPostWrongContentType(String uri) throws Exception {
        record("POST", uri, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(uri)
                .contentType("text/plain").content("hello")));
    }

    private void mvcMethodNotAllowed(String uri) throws Exception {
        record("GET", uri, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(uri)));
    }

    /** Finds the described operation for a real request, or null when the path is not described. */
    static JsonNode operationFor(JsonNode spec, String method, String uri) {
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        Iterator<String> templates = spec.get("paths").fieldNames();
        while (templates.hasNext()) {
            String template = templates.next();
            if (path.matches(template.replaceAll("\\{[^/]+}", "[^/]+"))) {
                return spec.get("paths").get(template).get(method.toLowerCase());
            }
        }
        return null;
    }

    @Test
    void everyRealResponseMatchesItsDocumentedStatusAndSchema() throws Exception {
        JsonNode spec = OpenApiFixture.spec(mvc);
        playThroughTheGame();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Exchange e : exchanges) {
            JsonNode operation = operationFor(spec, e.method(), e.uri());
            if (operation == null) {
                // A method the address does not offer (405) is described once, as the shared error envelope.
                if (e.status() < 400) {
                    problems.add(e.method() + " " + e.uri() + " succeeded but is not described at all");
                    continue;
                }
                JsonNode envelope = OpenApiFixture.JSON.createObjectNode().put("$ref", "#/components/schemas/ErrorEnvelope");
                for (String problem : SchemaConformance.check(spec, envelope, TestSupport.parse(e.body()))) {
                    problems.add(e.method() + " " + e.uri() + " -> " + e.status() + ": " + problem);
                }
                checked++;
                continue;
            }
            JsonNode response = operation.get("responses").get(Integer.toString(e.status()));
            if (response == null) {
                problems.add(e.method() + " " + e.uri() + " answered " + e.status() + " which is not documented");
                continue;
            }
            JsonNode schema = response.path("content").path("application/json").path("schema");
            if (schema.isMissingNode()) {
                problems.add(e.method() + " " + e.uri() + " " + e.status() + " has no documented body schema");
                continue;
            }
            for (String problem : SchemaConformance.check(spec, schema, TestSupport.parse(e.body()))) {
                problems.add(e.method() + " " + e.uri() + " -> " + e.status() + ": " + problem);
            }
            checked++;
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(checked >= 25, "scenario should exercise many exchanges, got " + checked);
    }

    @Test
    void everyWorkedExampleInTheDescriptionConformsToItsSchema() throws Exception {
        JsonNode spec = OpenApiFixture.spec(mvc);
        List<String> problems = new ArrayList<>();
        int examples = 0;
        Iterator<String> paths = spec.get("paths").fieldNames();
        while (paths.hasNext()) {
            String path = paths.next();
            Iterator<String> methods = spec.get("paths").get(path).fieldNames();
            while (methods.hasNext()) {
                String method = methods.next();
                JsonNode operation = spec.get("paths").get(path).get(method);
                JsonNode body = operation.path("requestBody").path("content").path("application/json");
                if (body.has("example")) {
                    examples++;
                    collect(problems, method + " " + path + " request", spec, body.get("schema"), body.get("example"));
                }
                Iterator<String> statuses = operation.get("responses").fieldNames();
                while (statuses.hasNext()) {
                    String status = statuses.next();
                    JsonNode json = operation.get("responses").get(status).path("content").path("application/json");
                    if (json.has("example")) {
                        examples++;
                        collect(problems, method + " " + path + " " + status, spec, json.get("schema"), json.get("example"));
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(examples >= 22, "expected a request or response example per outcome, got " + examples);
        assertEquals(0, problems.size());
    }

    private static void collect(List<String> problems, String label, JsonNode spec, JsonNode schema, JsonNode example) {
        for (String problem : SchemaConformance.check(spec, schema, example)) {
            problems.add("example of " + label + ": " + problem);
        }
    }
}
