package com.example.messagereader.reflection;

import com.example.messagereader.api.ParsedTrafficMessage;
import com.example.messagereader.api.RawTrafficPacket;
import com.example.messagereader.api.TrafficInterfaceDefinition;
import com.example.messagereader.api.TrafficMessageDefinition;
import com.example.schemautils.StructSizeCalculator;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;

public class ReflectionTrafficMessageParser {
    private final ReflectionObjectParser objectParser = new ReflectionObjectParser();
    private final ReflectionFieldExtractor fieldExtractor = new ReflectionFieldExtractor();
    private final ReflectionFieldReader fieldReader = new ReflectionFieldReader();

    public ParsedTrafficMessage parse(RawTrafficPacket rawPacket, TrafficInterfaceDefinition definition) {
        try {
            validateConfig(definition);

            byte[] payload = rawPacket.payload();
            ByteOrder byteOrder = parseByteOrder(definition.getByteOrder());

            int headerSizeBytes = StructSizeCalculator.calculateStructSize(definition.getHeaderType());

            if (payload.length < headerSizeBytes) {
                return unparsable(rawPacket, definition, "Payload shorter than configured header. payloadBytes=" + payload.length + ", headerBytes=" + headerSizeBytes);
            }

            byte[] headerBytes = Arrays.copyOfRange(payload, 0, headerSizeBytes);

            Object header = objectParser.parseHeader(definition.getHeaderType(), headerBytes, byteOrder);
            Map<String, Object> headerFields = fieldExtractor.extractFields(header);

            Object opcodeValue = fieldReader.readField(header, definition.getOpcodeFieldName());
            String opcodeKey = String.valueOf(opcodeValue);

            TrafficMessageDefinition messageDefinition = definition.getSupportedMessages().get(opcodeKey);

            if (messageDefinition == null) {
                return new ParsedTrafficMessage(
                        rawPacket,
                        definition.getName(),
                        "Unparsable",
                        null,
                        null,
                        headerFields,
                        Map.of(),
                        opcodeValue,
                        false,
                        "Unsupported opcode " + opcodeKey + " for interface " + definition.getName() + ". supportedOpcodes=" + definition.getSupportedMessages().keySet()
                );
            }

            Object message = objectParser.parseMessage(messageDefinition.getMessageClass(), payload, byteOrder);
            Map<String, Object> bodyFields = fieldExtractor.extractFields(message);

            String displayName = messageDefinition.getDisplayName();
            if (displayName == null || displayName.isBlank()) {
                displayName = simpleName(messageDefinition.getMessageClass());
            }

            return new ParsedTrafficMessage(
                    rawPacket,
                    definition.getName(),
                    displayName,
                    messageDefinition.getMessageClass(),
                    message,
                    headerFields,
                    bodyFields,
                    opcodeValue,
                    true,
                    null
            );
        } catch (Exception e) {
            return unparsable(rawPacket, definition, rootMessage(e));
        }
    }

    private ParsedTrafficMessage unparsable(RawTrafficPacket rawPacket, TrafficInterfaceDefinition definition, String error) {
        return new ParsedTrafficMessage(
                rawPacket,
                definition.getName(),
                "Unparsable",
                null,
                null,
                Map.of(),
                Map.of(),
                null,
                false,
                error
        );
    }

    private void validateConfig(TrafficInterfaceDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition is required");
        if (definition.getHeaderType() == null || definition.getHeaderType().isBlank()) throw new IllegalArgumentException("headerType is required for interface " + definition.getName());
        if (definition.getOpcodeFieldName() == null || definition.getOpcodeFieldName().isBlank()) throw new IllegalArgumentException("opcodeFieldName is required for interface " + definition.getName());
        if (definition.getSupportedMessages() == null || definition.getSupportedMessages().isEmpty()) throw new IllegalArgumentException("supportedMessages must not be empty for interface " + definition.getName());
    }

    private ByteOrder parseByteOrder(String value) {
        if (value == null || value.isBlank() || "BIG_ENDIAN".equalsIgnoreCase(value)) return ByteOrder.BIG_ENDIAN;
        if ("LITTLE_ENDIAN".equalsIgnoreCase(value)) return ByteOrder.LITTLE_ENDIAN;
        throw new IllegalArgumentException("Unsupported byteOrder: " + value);
    }

    private String simpleName(String className) {
        if (className == null) return "";
        int index = className.lastIndexOf('.');
        return index >= 0 ? className.substring(index + 1) : className;
    }

    private String rootMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
