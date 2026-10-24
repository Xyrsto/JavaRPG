package dev.javarpg.server.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;

/**
 * The wording and worked examples of the API description, kept in YAML files next to the code instead of in
 * annotations on the controllers: openapi/operations.yaml, openapi/schemas.yaml and openapi/errors.yaml.
 */
@Component
public class OpenApiWording {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final JsonNode operations = load("openapi/operations.yaml");
    private final JsonNode schemas = load("openapi/schemas.yaml");
    private final JsonNode errors = load("openapi/errors.yaml");

    public JsonNode operations() {
        return operations;
    }

    public JsonNode schemas() {
        return schemas;
    }

    public JsonNode errors() {
        return errors;
    }

    /** Converts a wording node to plain maps and lists, which is what the OpenAPI model serialises. */
    public static Object plain(JsonNode node) {
        return YAML.convertValue(node, Object.class);
    }

    private static JsonNode load(String resource) {
        try (InputStream in = OpenApiWording.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing API wording file " + resource);
            }
            return YAML.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read API wording file " + resource, e);
        }
    }
}
