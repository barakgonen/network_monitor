package com.example.monitor.api;

import com.example.monitor.rest.RestApiDefinition;
import com.example.monitor.rest.RestAutoReplySettingsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures the static HTTP response a REST server-mode interface writes back per operation -
 * see {@link RestAutoReplySettingsService} for why this is a mandatory synchronous response
 * rather than an optional async dispatch like {@code AutoReplyController}.
 */
@RestController
public class RestAutoReplyController extends AbstractBadRequestController {
    private final Map<String, RestApiDefinition> restApiDefinitions;
    private final RestAutoReplySettingsService autoReplySettingsService;

    public RestAutoReplyController(
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions,
            RestAutoReplySettingsService autoReplySettingsService
    ) {
        this.restApiDefinitions = restApiDefinitions;
        this.autoReplySettingsService = autoReplySettingsService;
    }

    @GetMapping("/api/rest/{key}/autoreply")
    public Map<String, RestAutoReplySettingsService.ResolvedReply> settings(@PathVariable("key") String key) {
        RestApiDefinition api = restApiDefinitions.get(key);
        if (api == null) {
            throw new IllegalArgumentException("Unknown REST interface: " + key);
        }

        Map<String, RestAutoReplySettingsService.ResolvedReply> resolved = new LinkedHashMap<>();
        api.operations().forEach(operation ->
                resolved.put(operation.operationId(), autoReplySettingsService.resolve(key, operation.operationId())));
        return resolved;
    }

    @PostMapping("/api/rest/{key}/autoreply/{operationId}")
    public RestAutoReplySettingsService.ResolvedReply update(
            @PathVariable("key") String key,
            @PathVariable("operationId") String operationId,
            @RequestBody UpdateRestAutoReplyRequest request
    ) {
        autoReplySettingsService.update(key, operationId, request.statusCode(), request.bodyTemplate());
        return autoReplySettingsService.resolve(key, operationId);
    }
}
