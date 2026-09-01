package com.example.publisher.metadata;

import com.example.binaryserdes.MessageType;
import com.example.restschema.RestApiDefinition;
import com.example.trafficconfig.InterfaceConfig;
import com.example.trafficconfig.TrafficToolConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists every interface this app can send to - branches on {@link InterfaceConfig#getProtocol()}/
 * {@link InterfaceConfig#hasSerdesFile()}, unifying serdes-backed UDP/TCP interfaces and
 * {@code protocol: REST} interfaces into one listing. Interfaces using the legacy hand-written
 * {@code messages:}/{@code definitionClass:} style (none currently configured in this repo) are
 * out of scope - this app only describes messages from {@code serdesFile}/{@code swaggerFile}, per
 * its whole reason for existing (no reflection on a {@code Class<?>}).
 */
@Component
public class PublishableInterfaceService {

    private final TrafficToolConfig trafficToolConfig;
    private final Map<String, List<MessageType>> interfaceMessageTypes;
    private final Map<String, RestApiDefinition> restApiDefinitions;

    public PublishableInterfaceService(
            TrafficToolConfig trafficToolConfig,
            Map<String, List<MessageType>> interfaceMessageTypes,
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions
    ) {
        this.trafficToolConfig = trafficToolConfig;
        this.interfaceMessageTypes = interfaceMessageTypes;
        this.restApiDefinitions = restApiDefinitions;
    }

    public List<PublishableInterfaceDto> list() {
        List<PublishableInterfaceDto> result = new ArrayList<>();

        for (InterfaceConfig interfaceConfig : trafficToolConfig.getInterfaces()) {
            if (isRest(interfaceConfig)) {
                result.add(restInterfaceDto(interfaceConfig));
            } else if (interfaceConfig.hasSerdesFile()) {
                result.add(serdesInterfaceDto(interfaceConfig));
            }
        }

        return result;
    }

    public InterfaceConfig requireInterfaceConfig(String key) {
        return trafficToolConfig.getInterfaces().stream()
                .filter(interfaceConfig -> key.equals(interfaceConfig.getKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown interface: " + key));
    }

    private boolean isRest(InterfaceConfig interfaceConfig) {
        return "REST".equalsIgnoreCase(interfaceConfig.getProtocol());
    }

    private PublishableInterfaceDto serdesInterfaceDto(InterfaceConfig interfaceConfig) {
        List<PublishableMessageDto> messages = interfaceMessageTypes
                .getOrDefault(interfaceConfig.getKey(), List.of()).stream()
                .map(messageType -> new PublishableMessageDto(messageType.getName(), messageType.getName()))
                .toList();

        return new PublishableInterfaceDto(
                interfaceConfig.getKey(), interfaceConfig.getName(), interfaceConfig.getProtocol(),
                interfaceConfig.getPort(), messages);
    }

    private PublishableInterfaceDto restInterfaceDto(InterfaceConfig interfaceConfig) {
        RestApiDefinition api = restApiDefinitions.get(interfaceConfig.getKey());
        List<PublishableMessageDto> messages = api == null ? List.of() : api.operations().stream()
                .map(operation -> new PublishableMessageDto(
                        operation.operationId(),
                        operation.httpMethod() + " " + operation.pathTemplate() + " (" + operation.operationId() + ")"))
                .toList();

        return new PublishableInterfaceDto(
                interfaceConfig.getKey(), interfaceConfig.getName(), "REST",
                interfaceConfig.getPort(), messages);
    }
}
