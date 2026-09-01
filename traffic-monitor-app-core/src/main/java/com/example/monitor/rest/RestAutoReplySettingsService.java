package com.example.monitor.rest;

import com.example.restschema.RestApiDefinition;
import com.example.restschema.RestOperationDefinition;
import com.example.restschema.RestSchemaNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the static HTTP response a REST server-mode interface writes back for a given
 * operation - REST's "auto-reply" is a mandatory synchronous HTTP response (every request gets
 * some response, by necessity of the protocol), unlike the deleted UDP/TCP handler-based
 * auto-reply mechanism (see CLAUDE.md's "Auto-reply removal").
 *
 * <p>{@link #resolve} always derives the response from the OpenAPI spec's own response schema:
 * its {@code example} if present, else a synthesized placeholder instance (empty
 * string/zero/false/empty array/nested object per leaf type). There is no per-operation
 * override capability - that used to be configurable via a "REST Auto-Reply" UI tab, which was
 * removed along with its settings-store backing.
 */
@Component
public class RestAutoReplySettingsService {
    private static final String DEFAULT_BODY = "{}";
    private static final int DEFAULT_STATUS = 200;

    private final Map<String, RestApiDefinition> restApiDefinitions;
    private final ObjectMapper objectMapper;

    public record ResolvedReply(int statusCode, String body) {
    }

    public RestAutoReplySettingsService(
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions,
            ObjectMapper objectMapper) {
        this.restApiDefinitions = restApiDefinitions;
        this.objectMapper = objectMapper;
    }

    public ResolvedReply resolve(String interfaceKey, String operationId) {
        return fallbackFromSpec(interfaceKey, operationId);
    }

    private ResolvedReply fallbackFromSpec(String interfaceKey, String operationId) {
        RestApiDefinition api = restApiDefinitions.get(interfaceKey);
        if (api == null) {
            return new ResolvedReply(DEFAULT_STATUS, DEFAULT_BODY);
        }

        Optional<RestOperationDefinition> operation = api.findByOperationId(operationId);
        if (operation.isEmpty()) {
            return new ResolvedReply(DEFAULT_STATUS, DEFAULT_BODY);
        }

        Map.Entry<String, RestSchemaNode> preferred = choosePreferredResponse(operation.get().responseSchemasByStatus());
        if (preferred == null) {
            return new ResolvedReply(DEFAULT_STATUS, DEFAULT_BODY);
        }

        Object value = preferred.getValue().example() != null ? preferred.getValue().example() : synthesizeExample(preferred.getValue());
        return new ResolvedReply(parseStatus(preferred.getKey()), toJson(value));
    }

    /** Prefers "200", else the lowest 2xx status present, else "default", else whatever's first. */
    private Map.Entry<String, RestSchemaNode> choosePreferredResponse(Map<String, RestSchemaNode> responses) {
        if (responses == null || responses.isEmpty()) {
            return null;
        }

        if (responses.containsKey("200")) {
            return Map.entry("200", responses.get("200"));
        }

        String best = null;
        for (String status : responses.keySet()) {
            if (status.matches("2\\d\\d") && (best == null || status.compareTo(best) < 0)) {
                best = status;
            }
        }
        if (best != null) {
            return Map.entry(best, responses.get(best));
        }

        if (responses.containsKey("default")) {
            return Map.entry("default", responses.get("default"));
        }

        return responses.entrySet().iterator().next();
    }

    private int parseStatus(String statusKey) {
        try {
            return Integer.parseInt(statusKey);
        } catch (NumberFormatException e) {
            return DEFAULT_STATUS;
        }
    }

    private Object synthesizeExample(RestSchemaNode node) {
        if (node == null) {
            return null;
        }

        if (node.example() != null) {
            return node.example();
        }

        return switch (node.type()) {
            case "object" -> {
                Map<String, Object> instance = new LinkedHashMap<>();
                if (node.properties() != null) {
                    for (RestSchemaNode property : node.properties()) {
                        instance.put(property.name(), synthesizeExample(property));
                    }
                }
                yield instance;
            }
            case "array" -> new ArrayList<>();
            case "integer", "number" -> 0;
            case "boolean" -> false;
            default -> "";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return DEFAULT_BODY;
        }
    }
}
