package com.example.schemas.ping;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageFields;
import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class PongMessageDefinition implements MessageDefinition {

    @Override
    public String interfaceName() {
        return "Ping Interface";
    }

    @Override
    public String messageType() {
        return "Pong";
    }

    @Override
    public int opcode() {
        return PingOpcodes.PONG;
    }

    @Override
    public Class<PongMessage> messageClass() {
        return PongMessage.class;
    }

    @Override
    public Map<String, Object> decodeBody(ByteBuffer body) {
        Map<String, Object> bodyFields = new LinkedHashMap<>();
        PingProtocolCodec.decodePongBody(body, bodyFields);
        return bodyFields;
    }

    @Override
    public ProtocolMessage decodeMessage(ByteBuffer body) {
        return fromFields(decodeBody(body));
    }

    @Override
    public byte[] encodeBody(Map<String, Object> fields) {
        return PingProtocolCodec.encodePongBody(fromFields(fields));
    }

    @Override
    public byte[] encodeBody(ProtocolMessage message) {
        return PingProtocolCodec.encodePongBody((PongMessage) message);
    }

    private PongMessage fromFields(Map<String, Object> fields) {
        return new PongMessage(MessageFields.requireInt(fields, "sequence"));
    }
}
