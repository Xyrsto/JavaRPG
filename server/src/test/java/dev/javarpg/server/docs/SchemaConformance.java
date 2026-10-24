package dev.javarpg.server.docs;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Checks a JSON value against an OpenAPI schema: types, enums, required fields, array items, and fields the
 * schema does not document (no additional properties are allowed). Null is accepted only for properties that
 * the schema does not list as required.
 */
final class SchemaConformance {

    private SchemaConformance() {
    }

    static List<String> check(JsonNode spec, JsonNode schema, JsonNode value) {
        List<String> problems = new ArrayList<>();
        walk(spec, schema, value, "$", true, problems);
        return problems;
    }

    private static void walk(JsonNode spec, JsonNode rawSchema, JsonNode value, String path, boolean mustExist,
                             List<String> problems) {
        JsonNode schema = OpenApiFixture.resolve(spec, rawSchema);
        if (value == null || value.isNull()) {
            if (mustExist) {
                problems.add(path + " is null but the description requires a value");
            }
            return;
        }
        String type = schema.path("type").asText("");
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : schema.get("enum")) {
                found |= allowed.equals(value);
            }
            if (!found) {
                problems.add(path + " value " + value + " is not one of " + schema.get("enum"));
            }
        }
        switch (type) {
            case "object" -> {
                if (!value.isObject()) {
                    problems.add(path + " should be an object but is " + value.getNodeType());
                    return;
                }
                checkObject(spec, schema, value, path, problems);
            }
            case "array" -> {
                if (!value.isArray()) {
                    problems.add(path + " should be an array but is " + value.getNodeType());
                    return;
                }
                int i = 0;
                for (JsonNode item : value) {
                    walk(spec, schema.path("items"), item, path + "[" + i++ + "]", true, problems);
                }
            }
            case "string" -> expect(value.isTextual(), path, "a string", value, problems);
            case "integer" -> expect(value.isIntegralNumber(), path, "an integer", value, problems);
            case "number" -> expect(value.isNumber(), path, "a number", value, problems);
            case "boolean" -> expect(value.isBoolean(), path, "a boolean", value, problems);
            default -> problems.add(path + " has a schema type this checker does not know: '" + type + "'");
        }
    }

    private static void checkObject(JsonNode spec, JsonNode schema, JsonNode value, String path, List<String> problems) {
        JsonNode properties = schema.path("properties");
        List<String> required = new ArrayList<>();
        schema.path("required").forEach(r -> required.add(r.asText()));
        for (String name : required) {
            if (!value.has(name)) {
                problems.add(path + "." + name + " is required by the description but missing");
            }
        }
        Iterator<String> names = value.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!properties.has(name)) {
                JsonNode additional = schema.path("additionalProperties");
                if (additional.isObject()) {
                    walk(spec, additional, value.get(name), path + "." + name, true, problems);
                } else {
                    problems.add(path + "." + name + " is returned but not documented");
                }
            } else {
                walk(spec, properties.get(name), value.get(name), path + "." + name, required.contains(name), problems);
            }
        }
    }

    private static void expect(boolean ok, String path, String what, JsonNode value, List<String> problems) {
        if (!ok) {
            problems.add(path + " should be " + what + " but is " + value);
        }
    }
}
