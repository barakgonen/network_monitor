package com.example.monitor;

import com.example.schemacore.ProtocolHeader;
import com.example.schemacore.ProtocolHeaderCodec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class TestProtocolPayloads {

    static final int ORANGE_OPCODE = 1001;
    static final int BANANA_OPCODE = 1002;
    static final int TEMPERATURE_READING_OPCODE = 2001;
    static final int PING_OPCODE = 3001;
    static final int PONG_OPCODE = 3002;

    private TestProtocolPayloads() {
    }

    static byte[] orange(String sourceFarm, byte freshnessCode) {
        byte[] farmBytes = sourceFarm.getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(Integer.BYTES + farmBytes.length + Byte.BYTES);
        body.putInt(farmBytes.length);
        body.put(farmBytes);
        body.put(freshnessCode);
        return ProtocolHeaderCodec.encodeMessage(ORANGE_OPCODE, System.currentTimeMillis(), body.array());
    }

    static byte[] banana(String color, double weight) {
        byte[] colorBytes = color.getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(Integer.BYTES + colorBytes.length + Double.BYTES);
        body.putInt(colorBytes.length);
        body.put(colorBytes);
        body.putDouble(weight);
        return ProtocolHeaderCodec.encodeMessage(BANANA_OPCODE, System.currentTimeMillis(), body.array());
    }

    static byte[] temperatureReading(String stationId, double temperatureCelsius, byte conditionCode) {
        byte[] stationBytes = stationId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(Integer.BYTES + stationBytes.length + Double.BYTES + Byte.BYTES);
        body.putInt(stationBytes.length);
        body.put(stationBytes);
        body.putDouble(temperatureCelsius);
        body.put(conditionCode);
        return ProtocolHeaderCodec.encodeMessage(TEMPERATURE_READING_OPCODE, System.currentTimeMillis(), body.array());
    }

    static byte[] ping(int sequence) {
        ByteBuffer body = ByteBuffer.allocate(Integer.BYTES);
        body.putInt(sequence);
        return ProtocolHeaderCodec.encodeMessage(PING_OPCODE, System.currentTimeMillis(), body.array());
    }

    static byte[] pong(int sequence) {
        ByteBuffer body = ByteBuffer.allocate(Integer.BYTES);
        body.putInt(sequence);
        return ProtocolHeaderCodec.encodeMessage(PONG_OPCODE, System.currentTimeMillis(), body.array());
    }

    static byte[] rawHeaderOnly(int opcode, int bodyLength) {
        return ProtocolHeaderCodec.encodeMessage(opcode, System.currentTimeMillis(), new byte[bodyLength]);
    }

    static byte[] tooShortForHeader() {
        return new byte[] {1, 2, 3};
    }

    static byte[] withBodyLengthMismatch() {
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolHeaderCodec.HEADER_SIZE_BYTES + 2);
        buffer.putInt(ORANGE_OPCODE);
        buffer.putLong(System.currentTimeMillis());
        buffer.putInt(999);
        buffer.put((byte) 1);
        buffer.put((byte) 2);
        return buffer.array();
    }

    static int decodeOpcode(byte[] payload) {
        return ByteBuffer.wrap(payload).getInt(0);
    }

    static int decodePongSequence(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);
        if (header.opcode() != PONG_OPCODE) {
            throw new AssertionError("Expected Pong opcode, got " + header.opcode());
        }
        return buffer.getInt();
    }

    static BananaFields decodeBananaBody(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);
        if (header.opcode() != BANANA_OPCODE) {
            throw new AssertionError("Expected Banana opcode, got " + header.opcode());
        }
        int colorLength = buffer.getInt();
        byte[] colorBytes = new byte[colorLength];
        buffer.get(colorBytes);
        double weight = buffer.getDouble();
        return new BananaFields(new String(colorBytes, StandardCharsets.UTF_8), weight);
    }

    record BananaFields(String color, double weight) {
    }
}
