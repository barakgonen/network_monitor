package com.example.schemas.fruit;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BananaMessageDefinitionTest {

    private final BananaMessageDefinition definition = new BananaMessageDefinition();

    @Test
    void interfaceName_and_messageType_and_opcode_and_messageClass_returnExpectedConstants() {
        assertThat(definition.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(definition.messageType()).isEqualTo("Banana");
        assertThat(definition.opcode()).isEqualTo(FruitOpcodes.BANANA);
        assertThat(definition.messageClass()).isEqualTo(BananaMessage.class);
    }

    @Test
    void decodeMessage_returnsTypedBananaMessage() throws Exception {
        BananaMessage original = new BananaMessage("yellow", 99.9);
        byte[] body = FruitProtocolCodec.encodeBananaBody(original);

        BananaMessage decoded = (BananaMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void encodeBody_fromFieldsMap_roundTripsWithDecodeMessage() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("color", "green");
        fields.put("weight", 12.5);

        byte[] body = definition.encodeBody(fields);
        BananaMessage decoded = (BananaMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new BananaMessage("green", 12.5));
    }

    @Test
    void encodeBody_fromProtocolMessageInstance_roundTripsWithDecodeMessage() throws Exception {
        BananaMessage message = new BananaMessage("brown", 5.0);

        byte[] body = definition.encodeBody((com.example.schemacore.ProtocolMessage) message);
        BananaMessage decoded = (BananaMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void encodeBody_withMissingColorField_throwsIllegalArgumentException() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("weight", 1.0);

        assertThatThrownBy(() -> definition.encodeBody(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeBody_withMissingWeightField_throwsIllegalArgumentException() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("color", "yellow");

        assertThatThrownBy(() -> definition.encodeBody(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
