package com.example.monitor.rest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the static HTTP response a REST server-mode interface writes back for a given
 * operation - REST's "auto-reply" is a mandatory synchronous HTTP response (every request gets
 * some response, by necessity of the protocol), not an optional async dispatch like {@code
 * AutoReplySettingsService}, so this is deliberately its own independent, in-memory-only settings
 * store (same pattern as {@code AutoReplySettingsService}: {@link ConcurrentHashMap}, no DB table,
 * no config-file seeding) rather than built on top of it.
 *
 * <p>When nothing's configured for an operation, {@link #resolve} falls back to the OpenAPI
 * spec's own response schema: its {@code example} if present, else a synthesized placeholder
 * instance (empty string/zero/false/empty array/nested object per leaf type).
 */
@Component
public class RestAutoReplySettingsService {
    private static final String DEFAULT_BODY = "{}";
    private static final int DEFAULT_STATUS = 200;

    private final Map<String, RestApiDefinition> restApiDefinitions;
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, RestAutoReplyConfig>> configuredByInterfaceAndOperation = new ConcurrentHashMap<>();

    public record ResolvedReply(int statusCode, String body) {
    }

    public RestAutoReplySettingsService(
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions,
            ObjectMapper objectMapper) {
        this.restApiDefinitions = restApiDefinitions;
        this.objectMapper = objectMapper;
    }

    public ResolvedReply resolve(String interfaceKey, String operationId) {
        RestAutoReplyConfig configured = configuredByInterfaceAndOperation
                .getOrDefault(interfaceKey, Map.of())
                .get(operationId);

        if (configured != null) {
            return new ResolvedReply(configured.statusCode(), configured.bodyTemplate());
        }

        return fallbackFromSpec(interfaceKey, operationId);
    }

    public Optional<RestAutoReplyConfig> configuredValue(String interfaceKey, String operationId) {
        return Optional.ofNullable(configuredByInterfaceAndOperation.getOrDefault(interfaceKey, Map.of()).get(operationId));
    }

    public void update(String interfaceKey, String operationId, int statusCode, String bodyTemplate) {
        configuredByInterfaceAndOperation
                .computeIfAbsent(interfaceKey, key -> new ConcurrentHashMap<>())
                .put(operationId, new RestAutoReplyConfig(statusCode, bodyTemplate));
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
