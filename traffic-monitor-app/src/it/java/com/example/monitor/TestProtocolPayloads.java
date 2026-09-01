package com.example.monitor;

import com.example.binaryserdes.envelope.ProtocolHeader;
import com.example.binaryserdes.envelope.ProtocolHeaderCodec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class TestProtocolPayloads {

    static final int ORANGE_OPCODE = 1001;
    static final int BANANA_OPCODE = 1002;
    static final int TEMPERATURE_READING_OPCODE = 2001;
    static final int PING_OPCODE = 3001;
    static final int PONG_OPCODE = 3002;
    static final int CANDY_OPCODE = 4001;

    /**
     * serdes/fruit.protocol.json declares {@code freshness} as a wire {@code string}, not a raw
     * byte code - mirrors {@code com.example.tester.schemas.fruit.FruitFreshness}'s wire names
     * (this module has zero schema dependency, so it can't import that enum directly).
     */
    private static final Map<Byte, String> FRESHNESS_WIRE_NAMES = Map.of(
            (byte) 1, "very_fresh",
            (byte) 2, "not_fresh",
            (byte) 3, "unknown");

    private TestProtocolPayloads() {
    }

    static byte[] orange(String sourceFarm, byte freshnessCode) {
        byte[] farmBytes = sourceFarm.getBytes(StandardCharsets.UTF_8);
        byte[] freshnessBytes = FRESHNESS_WIRE_NAMES.get(freshnessCode).getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(Integer.BYTES + farmBytes.length + Integer.BYTES + freshnessBytes.length);
        body.putInt(farmBytes.length);
        body.put(farmBytes);
        body.putInt(freshnessBytes.length);
        body.put(freshnessBytes);
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

    /**
     * serdes/weather.protocol.json declares {@code condition} as a wire {@code string}, not a raw
     * byte code - mirrors {@code com.example.tester.schemas.weather.WeatherCondition}'s wire names.
     */
    private static final Map<Byte, String> CONDITION_WIRE_NAMES = Map.of(
            (byte) 1, "sunny",
            (byte) 2, "cloudy",
            (byte) 3, "rainy",
            (byte) 4, "unknown");

    static byte[] temperatureReading(String stationId, double temperatureCelsius, byte conditionCode) {
        byte[] stationBytes = stationId.getBytes(StandardCharsets.UTF_8);
        byte[] conditionBytes = CONDITION_WIRE_NAMES.get(conditionCode).getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(
                Integer.BYTES + stationBytes.length + Double.BYTES + Integer.BYTES + conditionBytes.length);
        body.putInt(stationBytes.length);
        body.put(stationBytes);
        body.putDouble(temperatureCelsius);
        body.putInt(conditionBytes.length);
        body.put(conditionBytes);
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

    static byte[] candy(String name, double calories) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(Integer.BYTES + nameBytes.length + Double.BYTES);
        body.putInt(nameBytes.length);
        body.put(nameBytes);
        body.putDouble(calories);
        return ProtocolHeaderCodec.encodeMessage(CANDY_OPCODE, System.currentTimeMillis(), body.array());
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
