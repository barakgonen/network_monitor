package com.example.schemas.fruit;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageFields;
import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class BananaMessageDefinition implements MessageDefinition {

    @Override
    public String interfaceName() {
        return "Fruit Interface";
    }

    @Override
    public String messageType() {
        return "Banana";
    }

    @Override
    public int opcode() {
        return FruitOpcodes.BANANA;
    }

    @Override
    public Class<BananaMessage> messageClass() {
        return BananaMessage.class;
    }

    @Override
    public Map<String, Object> decodeBody(ByteBuffer body) {
        Map<String, Object> bodyFields = new LinkedHashMap<>();
        FruitProtocolCodec.decodeBananaBody(body, bodyFields);
        return bodyFields;
    }

    @Override
    public ProtocolMessage decodeMessage(ByteBuffer body) {
        return fromFields(decodeBody(body));
    }

    @Override
    public byte[] encodeBody(Map<String, Object> fields) {
        return FruitProtocolCodec.encodeBananaBody(fromFields(fields));
    }

    @Override
    public byte[] encodeBody(ProtocolMessage message) {
        return FruitProtocolCodec.encodeBananaBody((BananaMessage) message);
    }

    private BananaMessage fromFields(Map<String, Object> fields) {
        return new BananaMessage(
                MessageFields.requireString(fields, "color"),
                MessageFields.requireDouble(fields, "weight")
        );
    }
}
