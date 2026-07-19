package com.example.monitor.schema;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolMessage;
import com.example.schemacore.ReflectiveMessageDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class MessageSchemaWiringConfig {

    @Bean
    public TrafficToolConfig trafficToolConfig() {
        return new TrafficToolConfigLoader().load();
    }

    @Bean
    public MessageDefinitionRegistry messageDefinitionRegistry(TrafficToolConfig config) throws ReflectiveOperationException {
        List<MessageDefinition> definitions = new ArrayList<>();

        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            for (MessageConfig message : interfaceConfig.getMessages()) {
                definitions.add(resolveDefinition(interfaceConfig, message));
            }
        }

        return new MessageDefinitionRegistry(definitions);
    }

    /**
     * One scoped registry per dedicated-port interface (its own opcode space, independent of the
     * legacy global registry above), keyed by {@link InterfaceConfig#getKey()}.
     */
    @Bean
    public Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries(TrafficToolConfig config)
            throws ReflectiveOperationException {
        Map<String, MessageDefinitionRegistry> registries = new LinkedHashMap<>();

        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            if (!interfaceConfig.hasDedicatedPort()) {
                continue;
            }

            List<MessageDefinition> definitions = new ArrayList<>();
            for (MessageConfig message : interfaceConfig.getMessages()) {
                definitions.add(resolveDefinition(interfaceConfig, message));
            }

            registries.put(interfaceConfig.getKey(), new MessageDefinitionRegistry(definitions));
        }

        return registries;
    }

    private MessageDefinition resolveDefinition(InterfaceConfig interfaceConfig, MessageConfig message)
            throws ReflectiveOperationException {
        if (message.getDefinitionClass() != null && !message.getDefinitionClass().isBlank()) {
            Class<?> definitionClass = Class.forName(message.getDefinitionClass());
            return (MessageDefinition) definitionClass.getDeclaredConstructor().newInstance();
        }

        Class<?> messageClass = Class.forName(message.getMessageClass());
        return new ReflectiveMessageDefinition(
                interfaceConfig.getName(),
                message.getType(),
                message.getOpcode(),
                messageClass.asSubclass(ProtocolMessage.class));
    }
}
