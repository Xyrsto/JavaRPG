package dev.javarpg.server.docs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.springframework.test.web.servlet.MockMvc;

/** Shared access to the generated API description and the committed copy of it. */
final class OpenApiFixture {

    static final ObjectMapper JSON = new ObjectMapper();

    private OpenApiFixture() {
    }

    /** Repository root, whether Maven runs the tests from the module or from the root. */
    static Path repoRoot() {
        Path here = Path.of("").toAbsolutePath();
        return "server".equals(String.valueOf(here.getFileName())) ? here.getParent() : here;
    }

    static Path snapshot() {
        return repoRoot().resolve("docs").resolve("api").resolve("openapi.yaml");
    }

    static Path docsDir() {
        return repoRoot().resolve("docs");
    }

    static JsonNode spec(MockMvc mvc) throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        return JSON.readTree(json);
    }

    static String yaml(MockMvc mvc) throws Exception {
        return normalize(mvc.perform(get("/v3/api-docs.yaml")).andReturn().getResponse().getContentAsString());
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n");
    }

    /** Follows a "#/components/schemas/X" reference, or returns the node itself. */
    static JsonNode resolve(JsonNode spec, JsonNode node) {
        if (node != null && node.has("allOf") && node.get("allOf").size() == 1) {
            return resolve(spec, node.get("allOf").get(0));
        }
        if (node != null && node.has("$ref")) {
            String ref = node.get("$ref").asText();
            JsonNode target = spec.at(ref.substring(1));
            if (target.isMissingNode()) {
                throw new IllegalStateException("Unresolvable reference " + ref);
            }
            return resolve(spec, target);
        }
        return node;
    }
}
