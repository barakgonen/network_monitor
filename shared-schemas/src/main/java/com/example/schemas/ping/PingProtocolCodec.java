package com.example.schemas.ping;

import com.example.schemacore.ProtocolHeader;
import com.example.schemacore.ProtocolHeaderCodec;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class PingProtocolCodec {

    public EncodedPingMessage encodePing(PingMessage message, long sendTimeEpochMillis) {
        return new EncodedPingMessage(
                ProtocolHeaderCodec.encodeMessage(PingOpcodes.PING, sendTimeEpochMillis, encodePingBody(message)));
    }

    public EncodedPingMessage encodePong(PongMessage message, long sendTimeEpochMillis) {
        return new EncodedPingMessage(
                ProtocolHeaderCodec.encodeMessage(PingOpcodes.PONG, sendTimeEpochMillis, encodePongBody(message)));
    }

    public DecodedPingMessage decode(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);

        String messageType = PingOpcodes.messageType(header.opcode());
        Map<String, Object> bodyFields = new LinkedHashMap<>();

        if (header.opcode() == PingOpcodes.PING) {
            decodePingBody(buffer, bodyFields);
        } else if (header.opcode() == PingOpcodes.PONG) {
            decodePongBody(buffer, bodyFields);
        }

        return new DecodedPingMessage("Ping Interface", messageType, header, bodyFields);
    }

    static byte[] encodePingBody(PingMessage message) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(message.sequence());
        return buffer.array();
    }

    static byte[] encodePongBody(PongMessage message) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(message.sequence());
        return buffer.array();
    }

    static void decodePingBody(ByteBuffer buffer, Map<String, Object> bodyFields) {
        if (buffer.remaining() < Integer.BYTES) {
            throw new IllegalArgumentException("Ping body is too short");
        }

        bodyFields.put("sequence", buffer.getInt());
    }

    static void decodePongBody(ByteBuffer buffer, Map<String, Object> bodyFields) {
        if (buffer.remaining() < Integer.BYTES) {
            throw new IllegalArgumentException("Pong body is too short");
        }

        bodyFields.put("sequence", buffer.getInt());
    }

    public record EncodedPingMessage(byte[] payload) {
    }

    public record DecodedPingMessage(
            String interfaceName,
            String messageType,
            ProtocolHeader header,
            Map<String, Object> bodyFields
    ) {
    }
}
