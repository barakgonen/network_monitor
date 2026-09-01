package com.example.publisher.metadata;

import com.example.binaryserdes.MessageType;
import com.example.publisher.dto.FieldDto;
import com.example.publisher.rest.RestFieldMetadataService;
import com.example.publisher.serdes.SerdesFieldMetadataService;
import com.example.restschema.RestApiDefinition;
import com.example.restschema.RestOperationDefinition;
import com.example.trafficconfig.InterfaceConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Thin dispatcher: describes a serdes message's or REST operation's fields as a {@link FieldDto}
 * list, branching on the interface's protocol.
 */
@Component
public class FieldMetadataService {

    private final PublishableInterfaceService interfaceService;
    private final Map<String, List<MessageType>> interfaceMessageTypes;
    private final Map<String, RestApiDefinition> restApiDefinitions;
    private final SerdesFieldMetadataService serdesFieldMetadataService;
    private final RestFieldMetadataService restFieldMetadataService;

    public FieldMetadataService(
            PublishableInterfaceService interfaceService,
            Map<String, List<MessageType>> interfaceMessageTypes,
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions,
            SerdesFieldMetadataService serdesFieldMetadataService,
            RestFieldMetadataService restFieldMetadataService
    ) {
        this.interfaceService = interfaceService;
        this.interfaceMessageTypes = interfaceMessageTypes;
        this.restApiDefinitions = restApiDefinitions;
        this.serdesFieldMetadataService = serdesFieldMetadataService;
        this.restFieldMetadataService = restFieldMetadataService;
    }

    public List<FieldDto> describe(String interfaceKey, String messageId) {
        InterfaceConfig interfaceConfig = interfaceService.requireInterfaceConfig(interfaceKey);

        if ("REST".equalsIgnoreCase(interfaceConfig.getProtocol())) {
            RestApiDefinition api = restApiDefinitions.get(interfaceKey);
            if (api == null) {
                throw new IllegalArgumentException("No REST API definition for interface: " + interfaceKey);
            }
            RestOperationDefinition operation = api.findByOperationId(messageId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown operation: " + messageId));
            return restFieldMetadataService.describeFields(operation.requestBodySchema());
        }

        MessageType messageType = findMessageType(interfaceKey, messageId);
        return serdesFieldMetadataService.describeFields(messageType);
    }

    MessageType findMessageType(String interfaceKey, String messageId) {
        return interfaceMessageTypes.getOrDefault(interfaceKey, List.of()).stream()
                .filter(mt -> mt.getName().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown message type: " + messageId));
    }
}
