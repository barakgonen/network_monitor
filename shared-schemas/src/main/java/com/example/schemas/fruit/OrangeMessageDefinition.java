package com.example.schemas.fruit;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageFields;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class OrangeMessageDefinition implements MessageDefinition {

    @Override
    public String interfaceName() {
        return "Fruit Interface";
    }

    @Override
    public String messageType() {
        return "Orange";
    }

    @Override
    public int opcode() {
        return FruitOpcodes.ORANGE;
    }

    @Override
    public Map<String, Object> decodeBody(ByteBuffer body) {
        Map<String, Object> bodyFields = new LinkedHashMap<>();
        FruitProtocolCodec.decodeOrangeBody(body, bodyFields);
        return bodyFields;
    }

    @Override
    public byte[] encodeBody(Map<String, Object> fields) {
        OrangeMessage orange = new OrangeMessage(
                MessageFields.requireString(fields, "sourceFarm"),
                FruitFreshness.fromWireName(MessageFields.requireString(fields, "freshness"))
        );

        return FruitProtocolCodec.encodeOrangeBody(orange);
    }
}
