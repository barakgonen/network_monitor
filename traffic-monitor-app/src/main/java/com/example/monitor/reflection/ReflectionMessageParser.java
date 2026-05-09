package com.example.monitor.reflection;

import com.example.schemautils.StructSizeCalculator;

import com.example.monitor.config.TrafficMonitorProperties;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;

@Component
public class ReflectionMessageParser {
    private final ReflectionObjectParser objectParser;
    private final ReflectionFieldExtractor fieldExtractor;
    private final ReflectionFieldReader fieldReader;

    public ReflectionMessageParser(
            ReflectionObjectParser objectParser,
            ReflectionFieldExtractor fieldExtractor,
            ReflectionFieldReader fieldReader
    ) {
        this.objectParser = objectParser;
        this.fieldExtractor = fieldExtractor;
        this.fieldReader = fieldReader;
    }

    public ReflectionParseResult parse(byte[] payload, TrafficMonitorProperties.ReflectionInterface reflectionInterface) {
        try {
            validateConfig(reflectionInterface);

            ByteOrder byteOrder = parseByteOrder(reflectionInterface.getByteOrder());

            int headerSizeBytes = StructSizeCalculator.calculateStructSize(reflectionInterface.getHeaderType());

            if (payload.length < headerSizeBytes) {
                return ReflectionParseResult.unparsable(
                        "Payload shorter than configured header. payloadBytes="
                                + payload.length
                                + ", headerBytes="
                                + headerSizeBytes
                                + ", headerType="
                                + reflectionInterface.getHeaderType()
                );
            }

            byte[] headerBytes = Arrays.copyOfRange(payload, 0, headerSizeBytes);

            Object header = objectParser.parseHeader(
                    reflectionInterface.getHeaderType(),
                    headerBytes,
                    byteOrder
            );

            Map<String, Object> headerFields = fieldExtractor.extractFields(header);

            Object opcodeValue = fieldReader.readField(header, reflectionInterface.getOpcodeFieldName());
            String opcodeKey = String.valueOf(opcodeValue);

            TrafficMonitorProperties.SupportedMessage supportedMessage =
                    reflectionInterface.getSupportedMessages().get(opcodeKey);

            if (supportedMessage == null) {
                return ReflectionParseResult.unparsable(
                        "Unsupported opcode "
                                + opcodeKey
                                + " for interface "
                                + reflectionInterface.getName()
                                + ". supportedOpcodes="
                                + reflectionInterface.getSupportedMessages().keySet()
                );
            }

            Object message = objectParser.parseMessage(
                    supportedMessage.getMessageClass(),
                    payload,
                    byteOrder
            );

            Map<String, Object> bodyFields = fieldExtractor.extractFields(message);

            String displayName = supportedMessage.getDisplayName();

            if (displayName == null || displayName.isBlank()) {
                displayName = message.getClass().getSimpleName();
            }

            return ReflectionParseResult.parsed(
                    supportedMessage.getMessageClass(),
                    displayName,
                    message,
                    headerFields,
                    bodyFields,
                    opcodeValue
            );
        } catch (Exception e) {
            return ReflectionParseResult.unparsable(rootMessage(e));
        }
    }

    private void validateConfig(TrafficMonitorProperties.ReflectionInterface reflectionInterface) {
        if (reflectionInterface == null) {
            throw new IllegalArgumentException("reflectionInterface is required");
        }

        if (reflectionInterface.getHeaderType() == null || reflectionInterface.getHeaderType().isBlank()) {
            throw new IllegalArgumentException("header-type is required for interface " + reflectionInterface.getName());
        }

        if (reflectionInterface.getOpcodeFieldName() == null || reflectionInterface.getOpcodeFieldName().isBlank()) {
            throw new IllegalArgumentException("opcode-field-name is required for interface " + reflectionInterface.getName());
        }

        if (reflectionInterface.getSupportedMessages() == null || reflectionInterface.getSupportedMessages().isEmpty()) {
            throw new IllegalArgumentException("supported-messages must not be empty for interface " + reflectionInterface.getName());
        }

        for (Map.Entry<String, TrafficMonitorProperties.SupportedMessage> entry : reflectionInterface.getSupportedMessages().entrySet()) {
            if (entry.getValue() == null || entry.getValue().getMessageClass() == null || entry.getValue().getMessageClass().isBlank()) {
                throw new IllegalArgumentException("message-class is required for opcode " + entry.getKey());
            }
        }
    }

    private ByteOrder parseByteOrder(String value) {
        if (value == null || value.isBlank() || "BIG_ENDIAN".equalsIgnoreCase(value)) {
            return ByteOrder.BIG_ENDIAN;
        }

        if ("LITTLE_ENDIAN".equalsIgnoreCase(value)) {
            return ByteOrder.LITTLE_ENDIAN;
        }

        throw new IllegalArgumentException("Unsupported byte-order: " + value);
    }

    private String rootMessage(Exception e) {
        Throwable current = e;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
