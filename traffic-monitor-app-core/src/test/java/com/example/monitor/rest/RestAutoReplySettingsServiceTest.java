package com.example.monitor.rest;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestAutoReplySettingsServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestOperationDefinition operationWithResponses(Map<String, RestSchemaNode> responses) {
        return new RestOperationDefinition(
                "pets", "getPet", "GET", "/pets/{petId}",
                List.of(), List.of(), List.of(), null, responses, null, false);
    }

    private RestAutoReplySettingsService service(RestOperationDefinition operation) {
        RestApiDefinition api = new RestApiDefinition("pets", List.of(operation));
        return new RestAutoReplySettingsService(Map.of("pets", api), objectMapper);
    }

    @Test
    void resolve_withConfiguredValue_winsOverFallback() {
        RestOperationDefinition operation = operationWithResponses(Map.of());
        RestAutoReplySettingsService service = service(operation);

        service.update("pets", "getPet", 418, "{\"teapot\":true}");

        RestAutoReplySettingsService.ResolvedReply resolved = service.resolve("pets", "getPet");

        assertThat(resolved.statusCode()).isEqualTo(418);
        assertThat(resolved.body()).isEqualTo("{\"teapot\":true}");
    }

    @Test
    void resolve_withNoConfiguredValue_fallsBackToSchemaExample() {
        RestSchemaNode responseSchema = new RestSchemaNode("", "object",
                null, List.of(new RestSchemaNode("name", "string", null, null, null, "Rex", false, null)),
                null, null, false, null);

        RestOperationDefinition operation = operationWithResponses(Map.of("200", responseSchema));
        RestAutoReplySettingsService service = service(operation);

        RestAutoReplySettingsService.ResolvedReply resolved = service.resolve("pets", "getPet");

        assertThat(resolved.statusCode()).isEqualTo(200);
        assertThat(resolved.body()).contains("\"name\":\"Rex\"");
    }

    @Test
    void resolve_withNoExampleAnywhere_synthesizesPlaceholderInstance() {
        RestSchemaNode responseSchema = new RestSchemaNode("", "object", null,
                List.of(
                        new RestSchemaNode("name", "string", null, null, null, null, false, null),
                        new RestSchemaNode("age", "integer", null, null, null, null, false, null)),
                null, null, false, null);

        RestOperationDefinition operation = operationWithResponses(Map.of("200", responseSchema));
        RestAutoReplySettingsService service = service(operation);

        RestAutoReplySettingsService.ResolvedReply resolved = service.resolve("pets", "getPet");

        assertThat(resolved.body()).contains("\"name\":\"\"").contains("\"age\":0");
    }

    @Test
    void resolve_prefers200Response_overOtherStatuses() {
        RestSchemaNode okSchema = new RestSchemaNode("", "object", null, List.of(), null, Map.of("ok", true), false, null);
        RestSchemaNode errorSchema = new RestSchemaNode("", "object", null, List.of(), null, Map.of("error", true), false, null);

        RestOperationDefinition operation = operationWithResponses(Map.of("404", errorSchema, "200", okSchema));
        RestAutoReplySettingsService service = service(operation);

        RestAutoReplySettingsService.ResolvedReply resolved = service.resolve("pets", "getPet");

        assertThat(resolved.statusCode()).isEqualTo(200);
        assertThat(resolved.body()).contains("\"ok\":true");
    }

    @Test
    void resolve_withUnknownInterface_returnsDefault() {
        RestAutoReplySettingsService service = new RestAutoReplySettingsService(Map.of(), objectMapper);

        RestAutoReplySettingsService.ResolvedReply resolved = service.resolve("unknown", "getPet");

        assertThat(resolved.statusCode()).isEqualTo(200);
        assertThat(resolved.body()).isEqualTo("{}");
    }
}
