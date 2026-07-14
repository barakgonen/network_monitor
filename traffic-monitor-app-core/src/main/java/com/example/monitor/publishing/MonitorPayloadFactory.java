package com.example.monitor.publishing;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolHeaderCodec;
import com.example.schemacore.ProtocolMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class MonitorPayloadFactory {
    private final MessageDefinitionRegistry messageDefinitionRegistry;

    public MonitorPayloadFactory(MessageDefinitionRegistry messageDefinitionRegistry) {
        this.messageDefinitionRegistry = messageDefinitionRegistry;
    }

    public byte[] create(String interfaceName, String messageType, Map<String, Object> fields) {
        MessageDefinition definition = messageDefinitionRegistry.find(interfaceName, messageType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported message: interfaceName=" + interfaceName + ", messageType=" + messageType));

        try {
            byte[] body = definition.encodeBody(fields);
            return ProtocolHeaderCodec.encodeMessage(definition.opcode(), Instant.now().toEpochMilli(), body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encode message: " + e.getMessage(), e);
        }
    }

    public byte[] create(ProtocolMessage message) {
        MessageDefinition definition = messageDefinitionRegistry.findByMessageClass(message.getClass())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No MessageDefinition registered for message class " + message.getClass()));

        try {
            byte[] body = definition.encodeBody(message);
            return ProtocolHeaderCodec.encodeMessage(definition.opcode(), Instant.now().toEpochMilli(), body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encode message: " + e.getMessage(), e);
        }
    }
}
