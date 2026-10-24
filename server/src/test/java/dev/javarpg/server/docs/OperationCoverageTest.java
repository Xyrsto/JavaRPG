package dev.javarpg.server.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * FR-001, FR-002, FR-006: every operation the server maps appears in the description and vice versa, each one
 * carries its wording and worked examples, and the wording file has no stray entries.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationCoverageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    private Set<String> serverOperations() {
        Set<String> found = new TreeSet<>();
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().equals("dev.javarpg.server.controller")) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                for (var method : info.getMethodsCondition().getMethods()) {
                    found.add(method.name() + " " + pattern);
                }
            }
        }
        return found;
    }

    private Set<String> describedOperations(JsonNode spec) {
        Set<String> found = new TreeSet<>();
        Iterator<String> paths = spec.get("paths").fieldNames();
        while (paths.hasNext()) {
            String path = paths.next();
            Iterator<String> methods = spec.get("paths").get(path).fieldNames();
            while (methods.hasNext()) {
                found.add(methods.next().toUpperCase() + " " + path);
            }
        }
        return found;
    }

    @Test
    void theDescriptionListsExactlyTheOperationsTheServerOffers() throws Exception {
        Set<String> server = serverOperations();
        Set<String> described = describedOperations(OpenApiFixture.spec(mvc));
        assertEquals(11, server.size(), "ten game operations plus health: " + server);
        Set<String> missing = new TreeSet<>(server);
        missing.removeAll(described);
        Set<String> extra = new TreeSet<>(described);
        extra.removeAll(server);
        assertTrue(missing.isEmpty(), "in the server but not in the description: " + missing);
        assertTrue(extra.isEmpty(), "in the description but not in the server: " + extra);
    }

    @Test
    void everyOperationHasWordingAndWorkedExamples() throws Exception {
        JsonNode spec = OpenApiFixture.spec(mvc);
        Set<String> ids = new TreeSet<>();
        Iterator<String> paths = spec.get("paths").fieldNames();
        while (paths.hasNext()) {
            String path = paths.next();
            Iterator<String> methods = spec.get("paths").get(path).fieldNames();
            while (methods.hasNext()) {
                String method = methods.next();
                JsonNode op = spec.get("paths").get(path).get(method);
                String label = method.toUpperCase() + " " + path;
                assertTrue(op.path("operationId").asText().matches("[A-Z][A-Za-z]+_[a-z][A-Za-z]+"), label + " operationId");
                assertTrue(ids.add(op.get("operationId").asText()), label + " operationId must be unique");
                assertFalse(op.path("summary").asText().isBlank(), label + " needs a summary");
                assertTrue(op.path("description").asText().length() > 40, label + " needs a description");
                assertFalse(op.path("tags").isMissingNode(), label + " needs a tag");
                boolean hasSuccessExample = false;
                boolean hasErrorResponse = false;
                Iterator<String> statuses = op.get("responses").fieldNames();
                while (statuses.hasNext()) {
                    String status = statuses.next();
                    JsonNode json = op.get("responses").get(status).path("content").path("application/json");
                    boolean example = json.has("example") || json.has("examples");
                    if (status.startsWith("2")) {
                        hasSuccessExample |= example;
                    } else {
                        hasErrorResponse = true;
                        assertTrue(example, label + " response " + status + " needs an example");
                    }
                }
                assertTrue(hasSuccessExample, label + " needs a worked success example");
                boolean takesInput = op.has("parameters") || op.has("requestBody");
                assertTrue(hasErrorResponse || !takesInput, label + " takes input, so it needs at least one documented failure");
            }
        }
        assertEquals(11, ids.size());
    }

    @Test
    void theWordingFileHasAnEntryForEveryOperationAndNoStrayOnes() throws Exception {
        Set<String> ids = new TreeSet<>();
        JsonNode spec = OpenApiFixture.spec(mvc);
        spec.get("paths").forEach(path -> path.forEach(op -> ids.add(op.get("operationId").asText())));
        Set<String> keys = new TreeSet<>();
        try (InputStream in = getClass().getResourceAsStream("/openapi/operations.yaml")) {
            assertTrue(in != null, "server/src/main/resources/openapi/operations.yaml must exist");
            new ObjectMapper(new YAMLFactory()).readTree(in).fieldNames().forEachRemaining(keys::add);
        }
        assertEquals(ids, keys);
    }
}
