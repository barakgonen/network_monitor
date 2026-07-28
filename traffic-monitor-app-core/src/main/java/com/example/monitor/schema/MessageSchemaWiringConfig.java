package com.example.monitor.schema;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolMessage;
import com.example.schemacore.reflect.ReflectiveMessageDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class MessageSchemaWiringConfig {

    /**
     * Defaults to {@link TrafficToolConfigLoader}'s own env-var-or-default resolution (used by the
     * real app). Tests override {@code traffic.tool.config-path} via {@code @DynamicPropertySource}
     * to point at a per-Spring-context generated copy of the test YAML with freshly chosen free
     * ports, since {@link TrafficToolConfigLoader} reads a plain file path rather than Spring
     * properties and multiple test contexts can be alive at once (see AbstractIntegrationTestBase).
     */
    @Bean
    public TrafficToolConfig trafficToolConfig(@Value("${traffic.tool.config-path:}") String configPath) {
        TrafficToolConfigLoader loader = new TrafficToolConfigLoader();
        return configPath.isBlank() ? loader.load() : loader.load(Paths.get(configPath));
    }

    /**
     * Every message from every interface in one flat registry, keyed by opcode/name rather than by
     * interface. Not used for ingestion routing (each interface decodes against its own scoped
     * registry below) - this one backs cross-interface name/class lookups, namely
     * {@code MonitorPayloadFactory}'s "encode by interfaceName+messageType" and "encode by message
     * class" API.
     */
    @Bean
    public MessageDefinitionRegistry messageDefinitionRegistry(TrafficToolConfig config) throws ReflectiveOperationException {
        List<MessageDefinition> definitions = new ArrayList<>();

        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            definitions.addAll(buildDefinitions(interfaceConfig));
        }

        return new MessageDefinitionRegistry(definitions);
    }

    /**
     * One scoped registry per interface (its own opcode space), keyed by {@link InterfaceConfig#getKey()}.
     * This is what ingestion decodes against.
     */
    @Bean
    public Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries(TrafficToolConfig config)
            throws ReflectiveOperationException {
        Map<String, MessageDefinitionRegistry> registries = new LinkedHashMap<>();

        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            registries.put(interfaceConfig.getKey(), new MessageDefinitionRegistry(buildDefinitions(interfaceConfig)));
        }

        return registries;
    }

    private List<MessageDefinition> buildDefinitions(InterfaceConfig interfaceConfig) throws ReflectiveOperationException {
        List<MessageDefinition> definitions = new ArrayList<>();

        for (MessageConfig message : interfaceConfig.getMessages()) {
            definitions.add(resolveDefinition(interfaceConfig, message));
        }

        return definitions;
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
