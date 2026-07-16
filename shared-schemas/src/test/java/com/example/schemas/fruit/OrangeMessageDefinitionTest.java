package com.example.schemas.fruit;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrangeMessageDefinitionTest {

    private final OrangeMessageDefinition definition = new OrangeMessageDefinition();

    @Test
    void interfaceName_and_messageType_and_opcode_and_messageClass_returnExpectedConstants() {
        assertThat(definition.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(definition.messageType()).isEqualTo("Orange");
        assertThat(definition.opcode()).isEqualTo(FruitOpcodes.ORANGE);
        assertThat(definition.messageClass()).isEqualTo(OrangeMessage.class);
    }

    @Test
    void decodeMessage_returnsTypedOrangeMessage() throws Exception {
        OrangeMessage original = new OrangeMessage("farm", FruitFreshness.VERY_FRESH);
        byte[] body = FruitProtocolCodec.encodeOrangeBody(original);

        OrangeMessage decoded = (OrangeMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void encodeBody_fromFieldsMap_roundTripsWithDecodeMessage() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sourceFarm", "farm-x");
        fields.put("freshness", "very_fresh");

        byte[] body = definition.encodeBody(fields);
        OrangeMessage decoded = (OrangeMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new OrangeMessage("farm-x", FruitFreshness.VERY_FRESH));
    }

    @Test
    void encodeBody_fromProtocolMessageInstance_roundTripsWithDecodeMessage() throws Exception {
        OrangeMessage message = new OrangeMessage("farm-y", FruitFreshness.NOT_FRESH);

        byte[] body = definition.encodeBody((com.example.schemacore.ProtocolMessage) message);
        OrangeMessage decoded = (OrangeMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void encodeBody_withMissingSourceFarmField_throwsIllegalArgumentException() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("freshness", "very_fresh");

        assertThatThrownBy(() -> definition.encodeBody(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeBody_withMissingFreshnessField_throwsIllegalArgumentException() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sourceFarm", "farm");

        assertThatThrownBy(() -> definition.encodeBody(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeBody_withInvalidFreshnessWireName_fallsBackToUnknown() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sourceFarm", "farm");
        fields.put("freshness", "not-a-real-value");

        byte[] body = definition.encodeBody(fields);
        OrangeMessage decoded = (OrangeMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded.freshness()).isEqualTo(FruitFreshness.UNKNOWN);
    }
}
