package com.example.monitor.api;

import com.example.monitor.publisher.PublisherFieldDto;
import com.example.monitor.rest.RestApiDefinition;
import com.example.monitor.rest.RestFieldMetadataService;
import com.example.monitor.rest.RestInterfaceDto;
import com.example.monitor.rest.RestOperationDefinition;
import com.example.monitor.rest.RestOperationSummaryDto;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST analogue of {@code PublisherController}'s discovery endpoints - operations are listed from
 * {@link RestApiDefinition} (auto-discovered from each interface's swagger file) rather than a
 * hand-declared {@code messages:} list, and field metadata comes from {@link RestFieldMetadataService}
 * walking a {@link com.example.monitor.rest.RestSchemaNode} instead of reflecting a {@code Class<?>}.
 */
@RestController
public class RestOperationsController extends AbstractBadRequestController {
    private final TrafficToolConfig trafficToolConfig;
    private final Map<String, RestApiDefinition> restApiDefinitions;
    private final RestFieldMetadataService fieldMetadataService;

    public RestOperationsController(
            TrafficToolConfig trafficToolConfig,
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions,
            RestFieldMetadataService fieldMetadataService
    ) {
        this.trafficToolConfig = trafficToolConfig;
        this.restApiDefinitions = restApiDefinitions;
        this.fieldMetadataService = fieldMetadataService;
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

    @GetMapping("/api/rest/fields")
    public List<PublisherFieldDto> fields(
            @RequestParam("interfaceKey") String interfaceKey, @RequestParam("operationId") String operationId) {
        RestOperationDefinition operation = requireOperation(interfaceKey, operationId);
        return fieldMetadataService.describeFields(operation.requestBodySchema());
    }

    private RestOperationDefinition requireOperation(String interfaceKey, String operationId) {
        RestApiDefinition api = restApiDefinitions.get(interfaceKey);
        if (api == null) {
            throw new IllegalArgumentException("Unknown REST interface: " + interfaceKey);
        }

        return api.findByOperationId(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown operation: " + operationId));
    }
}
