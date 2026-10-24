package dev.javarpg.server.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** The checker itself must be able to fail, or the conformance tests would prove nothing. */
class SchemaConformanceTest {

    private static final String SPEC = """
        {"components":{"schemas":{"Item":{"type":"object","required":["id"],
          "properties":{"id":{"type":"string","enum":["a","b"]},"count":{"type":"integer"},
                        "tags":{"type":"array","items":{"type":"string"}}}}}}}
        """;

    private static JsonNode json(String text) throws Exception {
        return OpenApiFixture.JSON.readTree(text);
    }

    @Test
    void acceptsAMatchingValue() throws Exception {
        JsonNode spec = json(SPEC);
        JsonNode schema = json("{\"$ref\":\"#/components/schemas/Item\"}");
        assertEquals(0, SchemaConformance.check(spec, schema, json("{\"id\":\"a\",\"count\":2,\"tags\":[\"x\"]}")).size());
        assertEquals(0, SchemaConformance.check(spec, schema, json("{\"id\":\"b\",\"count\":null}")).size());
    }

    @Test
    void rejectsUndocumentedWrongTypedMissingAndOutOfEnumValues() throws Exception {
        JsonNode spec = json(SPEC);
        JsonNode schema = json("{\"$ref\":\"#/components/schemas/Item\"}");
        assertTrue(SchemaConformance.check(spec, schema, json("{\"id\":\"a\",\"extra\":1}")).get(0).contains("not documented"));
        assertTrue(SchemaConformance.check(spec, schema, json("{\"id\":\"a\",\"count\":\"x\"}")).get(0).contains("integer"));
        assertTrue(SchemaConformance.check(spec, schema, json("{\"count\":1}")).get(0).contains("missing"));
        assertTrue(SchemaConformance.check(spec, schema, json("{\"id\":\"z\"}")).get(0).contains("not one of"));
        assertTrue(SchemaConformance.check(spec, schema, json("{\"id\":null}")).get(0).contains("null"));
        assertTrue(SchemaConformance.check(spec, schema, json("{\"id\":\"a\",\"tags\":[1]}")).get(0).contains("string"));
    }
}
