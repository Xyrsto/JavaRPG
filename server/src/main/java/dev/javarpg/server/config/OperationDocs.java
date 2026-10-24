package dev.javarpg.server.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/**
 * Gives every operation a stable id (Controller_method) and fills in its wording, parameter text and worked
 * examples from openapi/operations.yaml. An operation without an entry stops the server from starting, so
 * a new endpoint cannot be added without documenting it.
 */
class OperationDocs implements OperationCustomizer {

    private static final String JSON = "application/json";

    private final OpenApiWording wording;

    OperationDocs(OpenApiWording wording) {
        this.wording = wording;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        String controller = handlerMethod.getBeanType().getSimpleName().replace("Controller", "");
        String id = controller + "_" + handlerMethod.getMethod().getName();
        JsonNode entry = wording.operations().get(id);
        if (entry == null) {
            throw new IllegalStateException("openapi/operations.yaml has no entry for operation " + id);
        }
        operation.setOperationId(id);
        operation.setSummary(entry.get("summary").asText());
        operation.setDescription(entry.get("description").asText());
        operation.setTags(List.of(entry.get("tag").asText()));
        describeParameters(operation, entry.path("parameters"));
        describeRequestBody(operation, entry);
        describeResponses(operation, entry.path("responses"));
        return operation;
    }

    private void describeParameters(Operation operation, JsonNode parameters) {
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : operation.getParameters()) {
            JsonNode text = parameters.get(parameter.getName());
            if (text == null) {
                throw new IllegalStateException("No wording for path parameter " + parameter.getName());
            }
            parameter.setDescription(text.get("description").asText());
            parameter.setExample(text.get("example").asText());
        }
    }

    private void describeRequestBody(Operation operation, JsonNode entry) {
        if (operation.getRequestBody() == null) {
            return;
        }
        JsonNode example = entry.get("requestExample");
        if (example == null) {
            throw new IllegalStateException("Operation " + operation.getOperationId() + " has a body but no requestExample");
        }
        operation.getRequestBody().setRequired(true);
        operation.getRequestBody().setDescription("A JSON object, sent with Content-Type application/json.");
        operation.getRequestBody().getContent().get(JSON).setExample(OpenApiWording.plain(example));
    }

    private void describeResponses(Operation operation, JsonNode responses) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        Iterator<Map.Entry<String, JsonNode>> entries = responses.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String status = entry.getKey();
            ApiResponse response = operation.getResponses().get(status);
            if (response == null) {
                response = new ApiResponse();
                response.setContent(new Content().addMediaType(JSON, new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorEnvelope"))));
                operation.getResponses().addApiResponse(status, response);
            }
            response.setDescription(entry.getValue().get("description").asText());
            MediaType media = response.getContent().get(JSON);
            if (media == null) {
                throw new IllegalStateException("Response " + status + " of " + operation.getOperationId() + " is not JSON");
            }
            media.setExample(OpenApiWording.plain(entry.getValue().get("example")));
        }
        for (String status : operation.getResponses().keySet()) {
            if (!responses.has(status)) {
                throw new IllegalStateException("Operation " + operation.getOperationId()
                        + " can answer " + status + " but openapi/operations.yaml does not describe it");
            }
        }
    }
}
