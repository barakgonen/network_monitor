package com.example.monitor.schema;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.reflect.ReflectiveMessageDefinition;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     *
     * <p>Two interfaces may legitimately reuse the same opcode and message class - e.g. the same
     * protocol wired up twice with a different {@code byteOrder} (see the {@code rada}/{@code
     * rada-le} interfaces). That's fine for ingestion (each interface decodes against its own
     * scoped registry below, unaffected by this method), but {@link MessageDefinitionRegistry}'s
     * opcode/class-keyed maps require global uniqueness - there's no way for "encode by class"
     * to know which interface's encoding a caller meant if the same class serves two. Rather than
     * relaxing that registry's invariant (real config typos, like copy-pasting an interface block
     * and forgetting to bump an opcode, should still fail fast), the first interface to register a
     * given opcode/class wins here and later ones are silently excluded from this flat view only -
     * {@code find(interfaceName, messageType)} and every scoped registry still see all of them.
     */
    @Bean
    public MessageDefinitionRegistry messageDefinitionRegistry(TrafficToolConfig config) throws ReflectiveOperationException {
        List<MessageDefinition> definitions = new ArrayList<>();
        Set<Integer> seenOpcodes = new HashSet<>();
        Set<Class<?>> seenMessageClasses = new HashSet<>();

        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            for (MessageDefinition definition : buildDefinitions(interfaceConfig)) {
                boolean opcodeAlreadyRegistered = !seenOpcodes.add(definition.opcode());
                boolean classAlreadyRegistered = !seenMessageClasses.add(definition.messageClass());

                if (!opcodeAlreadyRegistered && !classAlreadyRegistered) {
                    definitions.add(definition);
                }
            }
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
        String messageContext = "message " + message.getType() + " on interface " + interfaceConfig.getKey();
        requireReflectivelyCodable(messageClass, messageContext);

        return new ReflectiveMessageDefinition(
                interfaceConfig.getName(),
                message.getType(),
                message.getOpcode(),
                messageClass,
                resolveByteOrder(interfaceConfig, message));
    }

    /**
     * No interface is required of {@code messageClass:} classes (they may come from a dependency
     * this project doesn't control), so this is the only fail-fast check available at wiring time:
     * does the class actually expose one of {@link ReflectiveStructCodec}'s recognized
     * decode/encode shapes, rather than only discovering a mismatch on the first real message.
     */
    private void requireReflectivelyCodable(Class<?> messageClass, String context) {
        try {
            ReflectiveStructCodec.requireDecodable(messageClass);
            ReflectiveStructCodec.requireEncodable(messageClass);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(context + ": " + e.getMessage(), e);
        }
    }

    /**
     * {@link MessageConfig#getByteOrder()} overrides {@link InterfaceConfig#getByteOrder()}
     * when set, so an interface can carry a default order while individual message types opt
     * into a different one.
     */
    private ByteOrder resolveByteOrder(InterfaceConfig interfaceConfig, MessageConfig message) {
        if (message.getByteOrder() == null) {
            return interfaceConfig.resolveByteOrder();
        }
        return InterfaceConfig.parseByteOrder(
                message.getByteOrder(), "message " + message.getType() + " on interface " + interfaceConfig.getKey());
    }
}
