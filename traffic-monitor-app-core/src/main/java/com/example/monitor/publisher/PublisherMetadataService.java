package com.example.monitor.publisher;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.MessageConfig;
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
 * registries the ingestion pipeline uses, so opcode/messageClass are always accurate regardless
 * of whether a message uses a hand-written or reflective {@link MessageDefinition}.
 */
@Component
public class PublisherMetadataService {
    private final TrafficToolConfig trafficToolConfig;
    private final MessageDefinitionRegistry globalRegistry;
    private final Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries;

    public PublisherMetadataService(
            TrafficToolConfig trafficToolConfig,
            MessageDefinitionRegistry globalRegistry,
            @Qualifier("interfaceMessageDefinitionRegistries") Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries
    ) {
        this.trafficToolConfig = trafficToolConfig;
        this.globalRegistry = globalRegistry;
        this.interfaceMessageDefinitionRegistries = interfaceMessageDefinitionRegistries;
    }

    public List<PublisherInterfaceDto> interfaces() {
        List<PublisherInterfaceDto> result = new ArrayList<>();

        for (InterfaceConfig interfaceConfig : trafficToolConfig.getInterfaces()) {
            result.add(new PublisherInterfaceDto(
                    interfaceConfig.getKey(),
                    interfaceConfig.getName(),
                    messagesFor(interfaceConfig)));
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
        if (interfaceConfig.hasDedicatedPort()) {
            return interfaceMessageDefinitionRegistries.get(interfaceConfig.getKey());
        }
        return globalRegistry;
    }

    private List<PublisherMessageDto> messagesFor(InterfaceConfig interfaceConfig) {
        MessageDefinitionRegistry registry = registryFor(interfaceConfig);
        List<PublisherMessageDto> messages = new ArrayList<>();

        for (MessageConfig messageConfig : interfaceConfig.getMessages()) {
            MessageDefinition definition = registry.find(interfaceConfig.getName(), messageConfig.getType())
                    .orElseThrow(() -> new IllegalStateException(
                            "No MessageDefinition resolved for " + interfaceConfig.getName() + "/" + messageConfig.getType()));

            messages.add(new PublisherMessageDto(
                    messageConfig.getType(), definition.messageClass().getName(), definition.opcode()));
        }

        return messages;
    }
}
