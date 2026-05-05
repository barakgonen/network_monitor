package com.example.schemas.fruit;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class FruitProtocolCodec {
    private static final int HEADER_SIZE_BYTES = Integer.BYTES + Long.BYTES + Integer.BYTES;

    public EncodedFruitMessage encodeOrange(OrangeMessage orangeMessage, long sendTimeEpochMillis) {
        byte[] sourceFarmBytes = orangeMessage.sourceFarm().getBytes(StandardCharsets.UTF_8);

        int bodyLength = Integer.BYTES + sourceFarmBytes.length + Byte.BYTES;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES + bodyLength);

        buffer.putInt(FruitOpcodes.ORANGE);
        buffer.putLong(sendTimeEpochMillis);
        buffer.putInt(bodyLength);

        buffer.putInt(sourceFarmBytes.length);
        buffer.put(sourceFarmBytes);
        buffer.put(orangeMessage.freshness().getCode());

        return new EncodedFruitMessage(buffer.array());
    }

    public DecodedFruitMessage decode(byte[] payload) {
        if (payload.length < HEADER_SIZE_BYTES) {
            throw new IllegalArgumentException("Payload too short for Fruit header. actual=" + payload.length + ", required=" + HEADER_SIZE_BYTES);
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);

        int opcode = buffer.getInt();
        long sendTimeEpochMillis = buffer.getLong();
        int bodyLength = buffer.getInt();

        if (bodyLength < 0) {
            throw new IllegalArgumentException("Invalid negative bodyLength: " + bodyLength);
        }

        if (buffer.remaining() != bodyLength) {
            throw new IllegalArgumentException("Invalid bodyLength. header=" + bodyLength + ", actualRemaining=" + buffer.remaining());
        }

        String messageType = FruitOpcodes.messageType(opcode);
        Map<String, Object> bodyFields = new LinkedHashMap<>();

        if (opcode == FruitOpcodes.ORANGE) {
            if (buffer.remaining() < Integer.BYTES + Byte.BYTES) {
                throw new IllegalArgumentException("Orange body is too short");
            }

            int sourceFarmLength = buffer.getInt();

            if (sourceFarmLength < 0 || sourceFarmLength > buffer.remaining() - Byte.BYTES) {
                throw new IllegalArgumentException("Invalid sourceFarm length: " + sourceFarmLength);
            }

            byte[] sourceFarmBytes = new byte[sourceFarmLength];
            buffer.get(sourceFarmBytes);

            FruitFreshness freshness = FruitFreshness.fromCode(buffer.get());

            bodyFields.put("sourceFarm", new String(sourceFarmBytes, StandardCharsets.UTF_8));
            bodyFields.put("freshness", freshness.getWireName());
        }

        FruitProtocolHeader header = new FruitProtocolHeader(opcode, sendTimeEpochMillis, bodyLength);
        return new DecodedFruitMessage("Fruit Interface", messageType, header, bodyFields);
    }

    public record EncodedFruitMessage(byte[] payload) {
    }

    public record DecodedFruitMessage(
            String interfaceName,
            String messageType,
            FruitProtocolHeader header,
            Map<String, Object> bodyFields
    ) {
    }
}
