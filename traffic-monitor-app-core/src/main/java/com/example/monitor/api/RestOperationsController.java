package com.example.monitor.api;

import com.example.monitor.rest.RestApiDefinition;
import com.example.monitor.rest.RestInterfaceDto;
import com.example.monitor.rest.RestOperationSummaryDto;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists REST interfaces/operations discovered from each interface's swagger file - used by the
 * REST Auto-Reply panel's interface/operation dropdowns. Used to also serve field-description
 * (publish-only) via {@code /api/rest/fields}; that endpoint and {@code RestFieldMetadataService}
 * moved to sample-publisher-app along with the rest of publishing (see CLAUDE.md's "Publishing
 * lives in sample-publisher-app, not here").
 */
@RestController
public class RestOperationsController extends AbstractBadRequestController {
    private final TrafficToolConfig trafficToolConfig;
    private final Map<String, RestApiDefinition> restApiDefinitions;

    public RestOperationsController(
            TrafficToolConfig trafficToolConfig,
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions
    ) {
        this.trafficToolConfig = trafficToolConfig;
        this.restApiDefinitions = restApiDefinitions;
    }

    @GetMapping("/api/rest/interfaces")
    public List<RestInterfaceDto> interfaces() {
        List<RestInterfaceDto> result = new ArrayList<>();

        for (InterfaceConfig interfaceConfig : trafficToolConfig.getInterfaces()) {
            if (!"REST".equalsIgnoreCase(interfaceConfig.getProtocol())) {
                continue;
            }

            RestApiDefinition api = restApiDefinitions.get(interfaceConfig.getKey());
            List<RestOperationSummaryDto> operations = api == null ? List.of() : api.operations().stream()
                    .map(operation -> new RestOperationSummaryDto(
                            operation.operationId(), operation.httpMethod(), operation.pathTemplate(), operation.summary()))
                    .toList();

            result.add(new RestInterfaceDto(interfaceConfig.getKey(), interfaceConfig.getName(), operations));
        }

        return result;
    }
}
