package com.example.monitor.publisher;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists interfaces/messages available to the generic publisher, resolved from the same
 * per-interface scoped registries the ingestion pipeline uses, so opcode/messageClass are always
 * accurate regardless of whether a message uses a hand-written or reflective
 * {@link MessageDefinition}.
 */
@Component
public class PublisherMetadataService {
    private final TrafficToolConfig trafficToolConfig;
    private final Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries;

    public PublisherMetadataService(
            TrafficToolConfig trafficToolConfig,
            @Qualifier("interfaceMessageDefinitionRegistries") Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries
    ) {
        this.trafficToolConfig = trafficToolConfig;
        this.interfaceMessageDefinitionRegistries = interfaceMessageDefinitionRegistries;
    }

    public List<PublisherInterfaceDto> interfaces() {
        List<PublisherInterfaceDto> result = new ArrayList<>();

        for (InterfaceConfig interfaceConfig : trafficToolConfig.getInterfaces()) {
            result.add(interfaceDto(interfaceConfig));
        }

        return result;
    }

    public InterfaceConfig requireInterfaceConfig(String key) {
        return trafficToolConfig.getInterfaces().stream()
                .filter(interfaceConfig -> key.equals(interfaceConfig.getKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown interface: " + key));
    }

    public MessageDefinitionRegistry registryFor(InterfaceConfig interfaceConfig) {
        return interfaceMessageDefinitionRegistries.get(interfaceConfig.getKey());
    }

    /**
     * Reads messages from the interface's own scoped {@link MessageDefinitionRegistry} rather
     * than {@link InterfaceConfig#getMessages()} - the latter is {@code null} for serdes-backed
     * interfaces (their messages are auto-discovered from {@code serdesFile}, never hand-listed
     * under {@code messages:}), so it can't be relied on here. The registry is the one place both
     * legacy (hand-listed) and serdes-backed (auto-discovered) interfaces agree on their full
     * message set. The interface's display name is likewise taken from the first definition (for
     * serdes-backed interfaces this is the protocol file's own {@code interfaceName}, falling back
     * to the YAML {@code name:} - see {@code MessageSchemaWiringConfig.buildSerdesDefinitions})
     * rather than blindly from YAML, so the two can't silently disagree.
     */
    private PublisherInterfaceDto interfaceDto(InterfaceConfig interfaceConfig) {
        // REST interfaces have no MessageDefinitionRegistry entry at all (see
        // MessageSchemaWiringConfig) - they're listed separately via /api/rest/interfaces instead.
        if ("REST".equalsIgnoreCase(interfaceConfig.getProtocol())) {
            return new PublisherInterfaceDto(interfaceConfig.getKey(), interfaceConfig.getName(), List.of());
        }

        List<MessageDefinition> definitions = registryFor(interfaceConfig).all();
        String displayName = definitions.isEmpty() ? interfaceConfig.getName() : definitions.get(0).interfaceName();

        List<PublisherMessageDto> messages = definitions.stream()
                .map(definition -> new PublisherMessageDto(
                        definition.messageType(),
                        definition.messageClass() != null ? definition.messageClass().getName() : null,
                        definition.opcode()))
                .toList();

        return new PublisherInterfaceDto(interfaceConfig.getKey(), displayName, messages);
    }
}
