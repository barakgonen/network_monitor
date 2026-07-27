package com.example.schemacore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable lookup of {@link MessageDefinition}s by opcode, by (interfaceName, messageType), and
 * by message class. One registry is built per ingestion path (the legacy global registry, plus
 * one per dedicated-port interface) by {@code MessageSchemaWiringConfig}, which resolves each
 * configured message/definition class name from YAML with {@code Class.forName}. {@link
 * #loadFromClassNames(List)} is an alternate, simpler construction path (definition classes only,
 * no reflective message classes) kept for direct programmatic/test use.
 */
public final class MessageDefinitionRegistry {
    private final Map<Integer, MessageDefinition> byOpcode;
    private final Map<String, MessageDefinition> byInterfaceAndType;
    private final Map<Class<?>, MessageDefinition> byMessageClass;

    public MessageDefinitionRegistry(List<MessageDefinition> definitions) {
        Map<Integer, MessageDefinition> opcodeMap = new HashMap<>();
        Map<String, MessageDefinition> typeMap = new HashMap<>();
        Map<Class<?>, MessageDefinition> classMap = new HashMap<>();

        for (MessageDefinition definition : definitions) {
            String typeKey = key(definition.interfaceName(), definition.messageType());

            putUnique(opcodeMap, definition.opcode(), definition, "opcode " + definition.opcode());
            putUnique(typeMap, typeKey, definition, typeKey);
            putUnique(classMap, definition.messageClass(), definition, "message class " + definition.messageClass());
        }

        this.byOpcode = Map.copyOf(opcodeMap);
        this.byInterfaceAndType = Map.copyOf(typeMap);
        this.byMessageClass = Map.copyOf(classMap);
    }

    public static MessageDefinitionRegistry loadFromClassNames(List<String> classNames) throws ReflectiveOperationException {
        List<MessageDefinition> definitions = new ArrayList<>();

        for (String className : classNames) {
            Class<?> clazz = Class.forName(className, true, MessageDefinitionRegistry.class.getClassLoader());
            Object instance = clazz.getDeclaredConstructor().newInstance();
            definitions.add((MessageDefinition) instance);
        }

        return new MessageDefinitionRegistry(definitions);
    }

    public Optional<MessageDefinition> findByOpcode(int opcode) {
        return Optional.ofNullable(byOpcode.get(opcode));
    }

    public Optional<MessageDefinition> find(String interfaceName, String messageType) {
        return Optional.ofNullable(byInterfaceAndType.get(key(interfaceName, messageType)));
    }

    public Optional<MessageDefinition> findByMessageClass(Class<?> messageClass) {
        return Optional.ofNullable(byMessageClass.get(messageClass));
    }

    private static String key(String interfaceName, String messageType) {
        return interfaceName + "::" + messageType;
    }

    private static <K> void putUnique(Map<K, MessageDefinition> map, K key, MessageDefinition definition, String description) {
        if (map.putIfAbsent(key, definition) != null) {
            throw new IllegalStateException("Duplicate MessageDefinition registered for " + description);
        }
    }
}
