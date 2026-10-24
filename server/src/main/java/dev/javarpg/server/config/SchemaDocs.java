package dev.javarpg.server.config;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.server.dto.ApiDtos.ErrorEnvelope;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Fills the generated schemas with wording from openapi/schemas.yaml: descriptions, limits, which fields are
 * optional, which are withheld until a quest is completed, and the error code table. A schema or field
 * without wording stops the server from starting.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class SchemaDocs implements OpenApiCustomizer {

    static final String WITHHELD = "x-withheld-until-completed";
    static final String NEVER_FOR_HIDDEN = "x-never-shown-for-hidden-tests";
    static final String ERROR_CODES = "x-error-codes";

    private final OpenApiWording wording;

    SchemaDocs(OpenApiWording wording) {
        this.wording = wording;
    }

    @Override
    public void customise(OpenAPI openApi) {
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        ModelConverters.getInstance().readAll(ErrorEnvelope.class).forEach(schemas::putIfAbsent);
        for (Map.Entry<String, Schema> schema : new ArrayList<>(schemas.entrySet())) {
            describe(schema.getKey(), schema.getValue());
        }
        addErrorCodes(schemas.get("ErrorBody"));
    }

    private void describe(String name, Schema schema) {
        JsonNode entry = wording.schemas().get(name);
        if (entry == null) {
            throw new IllegalStateException("openapi/schemas.yaml has no entry for schema " + name);
        }
        schema.setDescription(entry.get("description").asText());
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        List<String> optional = new ArrayList<>();
        entry.path("optional").forEach(n -> optional.add(n.asText()));
        for (String field : new ArrayList<>(properties.keySet())) {
            JsonNode text = entry.path("properties").get(field);
            if (text == null) {
                throw new IllegalStateException("openapi/schemas.yaml has no wording for " + name + "." + field);
            }
            properties.put(field, describeField(properties.get(field), text));
        }
        for (String field : optional) {
            if (!properties.containsKey(field)) {
                throw new IllegalStateException(name + " lists optional field " + field + " which does not exist");
            }
        }
        TreeSet<String> required = new TreeSet<>(properties.keySet());
        required.removeAll(optional);
        schema.setRequired(required.isEmpty() ? null : new ArrayList<>(required));
    }

    private Schema describeField(Schema field, JsonNode text) {
        Schema target = field;
        if (field.get$ref() != null) {
            // Siblings of a plain reference are ignored by OpenAPI 3.0, so wrap it to keep the wording and markers.
            target = new Schema();
            target.setAllOf(List.of(field));
        }
        target.setDescription(text.get("description").asText());
        if (text.has("enum")) {
            List<String> values = new ArrayList<>();
            text.get("enum").forEach(v -> values.add(v.asText()));
            target.setEnum(values);
        }
        if (text.has("maxLength")) {
            target.setMaxLength(text.get("maxLength").asInt());
        }
        if (text.has("maxItems")) {
            target.setMaxItems(text.get("maxItems").asInt());
        }
        if (text.has("minItems")) {
            target.setMinItems(text.get("minItems").asInt());
        }
        if (text.path("withheld").asBoolean(false)) {
            target.addExtension(WITHHELD, true);
        }
        if (text.path("hiddenBlank").asBoolean(false)) {
            target.addExtension(NEVER_FOR_HIDDEN, true);
        }
        return target;
    }

    private void addErrorCodes(Schema errorBody) {
        TreeSet<String> codes = new TreeSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            codes.add(code.name());
        }
        TreeSet<String> wordingCodes = new TreeSet<>();
        wording.errors().fieldNames().forEachRemaining(wordingCodes::add);
        if (!wordingCodes.equals(codes)) {
            throw new IllegalStateException("openapi/errors.yaml must have exactly the ErrorCode entries; it has " + wordingCodes
                    + " but the enum has " + codes);
        }
        Map<String, Object> table = new LinkedHashMap<>();
        for (String code : codes) {
            JsonNode entry = wording.errors().get(code);
            if (entry == null) {
                throw new IllegalStateException("openapi/errors.yaml has no entry for error code " + code);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", entry.get("status").asInt());
            row.put("meaning", entry.get("meaning").asText());
            table.put(code, row);
        }
        ((Schema) errorBody.getProperties().get("code")).setEnum(new ArrayList<>(codes));
        errorBody.addExtension(ERROR_CODES, table);
    }
}
