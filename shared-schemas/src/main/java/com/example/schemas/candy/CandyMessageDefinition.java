package com.example.schemas.candy;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageFields;
import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class CandyMessageDefinition implements MessageDefinition {

    @Override
    public String interfaceName() {
        return "Candy Interface";
    }

    @Override
    public String messageType() {
        return "Candy";
    }

    @Override
    public int opcode() {
        return CandyOpcodes.CANDY;
    }

    @Override
    public Class<CandyMessage> messageClass() {
        return CandyMessage.class;
    }

    @Override
    public Map<String, Object> decodeBody(ByteBuffer body) {
        Map<String, Object> bodyFields = new LinkedHashMap<>();
        CandyProtocolCodec.decodeCandyBody(body, bodyFields);
        return bodyFields;
    }

    @Override
    public ProtocolMessage decodeMessage(ByteBuffer body) {
        return fromFields(decodeBody(body));
    }

    @Override
    public byte[] encodeBody(Map<String, Object> fields) {
        return CandyProtocolCodec.encodeCandyBody(fromFields(fields));
    }

    @Override
    public byte[] encodeBody(ProtocolMessage message) {
        return CandyProtocolCodec.encodeCandyBody((CandyMessage) message);
    }

    private CandyMessage fromFields(Map<String, Object> fields) {
        return new CandyMessage(
                MessageFields.requireString(fields, "name"),
                MessageFields.requireDouble(fields, "calories")
        );
    }
}
