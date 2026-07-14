package com.example.schemacore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MessageDefinitionRegistry {
    private final Map<Integer, MessageDefinition> byOpcode;
    private final Map<String, MessageDefinition> byInterfaceAndType;

    public MessageDefinitionRegistry(List<MessageDefinition> definitions) {
        Map<Integer, MessageDefinition> opcodeMap = new HashMap<>();
        Map<String, MessageDefinition> typeMap = new HashMap<>();

        for (MessageDefinition definition : definitions) {
            if (opcodeMap.putIfAbsent(definition.opcode(), definition) != null) {
                throw new IllegalStateException("Duplicate MessageDefinition registered for opcode " + definition.opcode());
            }

            String typeKey = key(definition.interfaceName(), definition.messageType());
            if (typeMap.putIfAbsent(typeKey, definition) != null) {
                throw new IllegalStateException("Duplicate MessageDefinition registered for " + typeKey);
            }
        }

        this.byOpcode = Map.copyOf(opcodeMap);
        this.byInterfaceAndType = Map.copyOf(typeMap);
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

    private static String key(String interfaceName, String messageType) {
        return interfaceName + "::" + messageType;
    }
}
