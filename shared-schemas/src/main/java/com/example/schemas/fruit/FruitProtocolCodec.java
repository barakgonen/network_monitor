package com.example.schemas.fruit;

import com.example.schemacore.ProtocolHeader;
import com.example.schemacore.ProtocolHeaderCodec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class FruitProtocolCodec {

    public EncodedFruitMessage encodeOrange(OrangeMessage orangeMessage, long sendTimeEpochMillis) {
        byte[] body = encodeOrangeBody(orangeMessage);
        return new EncodedFruitMessage(ProtocolHeaderCodec.encodeMessage(FruitOpcodes.ORANGE, sendTimeEpochMillis, body));
    }

    public EncodedFruitMessage encodeBanana(BananaMessage bananaMessage, long sendTimeEpochMillis) {
        byte[] body = encodeBananaBody(bananaMessage);
        return new EncodedFruitMessage(ProtocolHeaderCodec.encodeMessage(FruitOpcodes.BANANA, sendTimeEpochMillis, body));
    }

    public DecodedFruitMessage decode(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);

        String messageType = FruitOpcodes.messageType(header.opcode());
        Map<String, Object> bodyFields = new LinkedHashMap<>();

        if (header.opcode() == FruitOpcodes.ORANGE) {
            decodeOrangeBody(buffer, bodyFields);
        } else if (header.opcode() == FruitOpcodes.BANANA) {
            decodeBananaBody(buffer, bodyFields);
        }

        FruitProtocolHeader fruitHeader = new FruitProtocolHeader(header.opcode(), header.sendTimeEpochMillis(), header.bodyLength());
        return new DecodedFruitMessage("Fruit Interface", messageType, fruitHeader, bodyFields);
    }

    static byte[] encodeOrangeBody(OrangeMessage orangeMessage) {
        byte[] sourceFarmBytes = orangeMessage.sourceFarm().getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + sourceFarmBytes.length + Byte.BYTES);
        buffer.putInt(sourceFarmBytes.length);
        buffer.put(sourceFarmBytes);
        buffer.put(orangeMessage.freshness().getCode());

        return buffer.array();
    }

    static byte[] encodeBananaBody(BananaMessage bananaMessage) {
        byte[] colorBytes = bananaMessage.color().getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + colorBytes.length + Double.BYTES);
        buffer.putInt(colorBytes.length);
        buffer.put(colorBytes);
        buffer.putDouble(bananaMessage.weight());

        return buffer.array();
    }

    static void decodeOrangeBody(ByteBuffer buffer, Map<String, Object> bodyFields) {
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

    static void decodeBananaBody(ByteBuffer buffer, Map<String, Object> bodyFields) {
        if (buffer.remaining() < Integer.BYTES + Double.BYTES) {
            throw new IllegalArgumentException("Banana body is too short");
        }

        int colorLength = buffer.getInt();

        if (colorLength < 0 || colorLength > buffer.remaining() - Double.BYTES) {
            throw new IllegalArgumentException("Invalid color length: " + colorLength);
        }

        byte[] colorBytes = new byte[colorLength];
        buffer.get(colorBytes);

        double weight = buffer.getDouble();

        bodyFields.put("color", new String(colorBytes, StandardCharsets.UTF_8));
        bodyFields.put("weight", weight);
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
