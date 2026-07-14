package com.example.schemas.ping;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageFields;
import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class PingMessageDefinition implements MessageDefinition {

    @Override
    public String interfaceName() {
        return "Ping Interface";
    }

    @Override
    public String messageType() {
        return "Ping";
    }

    @Override
    public int opcode() {
        return PingOpcodes.PING;
    }

    @Override
    public Class<PingMessage> messageClass() {
        return PingMessage.class;
    }

    @Override
    public Map<String, Object> decodeBody(ByteBuffer body) {
        Map<String, Object> bodyFields = new LinkedHashMap<>();
        PingProtocolCodec.decodePingBody(body, bodyFields);
        return bodyFields;
    }

    @Override
    public ProtocolMessage decodeMessage(ByteBuffer body) {
        return fromFields(decodeBody(body));
    }

    @Override
    public byte[] encodeBody(Map<String, Object> fields) {
        return PingProtocolCodec.encodePingBody(fromFields(fields));
    }

    @Override
    public byte[] encodeBody(ProtocolMessage message) {
        return PingProtocolCodec.encodePingBody((PingMessage) message);
    }

    private PingMessage fromFields(Map<String, Object> fields) {
        return new PingMessage(MessageFields.requireInt(fields, "sequence"));
    }
}
