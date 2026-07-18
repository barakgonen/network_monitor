package com.example.schemas.candy;

import com.example.schemacore.ProtocolHeader;
import com.example.schemacore.ProtocolHeaderCodec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class CandyProtocolCodec {

    public EncodedCandyMessage encodeCandy(CandyMessage message, long sendTimeEpochMillis) {
        return new EncodedCandyMessage(
                ProtocolHeaderCodec.encodeMessage(CandyOpcodes.CANDY, sendTimeEpochMillis, encodeCandyBody(message)));
    }

    public DecodedCandyMessage decode(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);

        String messageType = CandyOpcodes.messageType(header.opcode());
        Map<String, Object> bodyFields = new LinkedHashMap<>();

        if (header.opcode() == CandyOpcodes.CANDY) {
            decodeCandyBody(buffer, bodyFields);
        }

        return new DecodedCandyMessage("Candy Interface", messageType, header, bodyFields);
    }

    static byte[] encodeCandyBody(CandyMessage message) {
        byte[] nameBytes = message.name().getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + nameBytes.length + Double.BYTES);
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);
        buffer.putDouble(message.calories());

        return buffer.array();
    }

    static void decodeCandyBody(ByteBuffer buffer, Map<String, Object> bodyFields) {
        if (buffer.remaining() < Integer.BYTES + Double.BYTES) {
            throw new IllegalArgumentException("Candy body is too short");
        }

        int nameLength = buffer.getInt();

        if (nameLength < 0 || nameLength > buffer.remaining() - Double.BYTES) {
            throw new IllegalArgumentException("Invalid name length: " + nameLength);
        }

        byte[] nameBytes = new byte[nameLength];
        buffer.get(nameBytes);

        double calories = buffer.getDouble();

        bodyFields.put("name", new String(nameBytes, StandardCharsets.UTF_8));
        bodyFields.put("calories", calories);
    }

    public record EncodedCandyMessage(byte[] payload) {
    }

    public record DecodedCandyMessage(
            String interfaceName,
            String messageType,
            ProtocolHeader header,
            Map<String, Object> bodyFields
    ) {
    }
}
