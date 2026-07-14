package com.example.schemas.fruit;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageFields;

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
    public Map<String, Object> decodeBody(ByteBuffer body) {
        Map<String, Object> bodyFields = new LinkedHashMap<>();
        FruitProtocolCodec.decodeBananaBody(body, bodyFields);
        return bodyFields;
    }

    @Override
    public byte[] encodeBody(Map<String, Object> fields) {
        BananaMessage banana = new BananaMessage(
                MessageFields.requireString(fields, "color"),
                MessageFields.requireDouble(fields, "weight")
        );

        return FruitProtocolCodec.encodeBananaBody(banana);
    }
}
