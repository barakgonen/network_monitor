package com.example.schemas.candy;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandyMessageDefinitionTest {

    private final CandyMessageDefinition definition = new CandyMessageDefinition();

    @Test
    void interfaceName_and_messageType_and_opcode_and_messageClass_returnExpectedConstants() {
        assertThat(definition.interfaceName()).isEqualTo("Candy Interface");
        assertThat(definition.messageType()).isEqualTo("Candy");
        assertThat(definition.opcode()).isEqualTo(CandyOpcodes.CANDY);
        assertThat(definition.messageClass()).isEqualTo(CandyMessage.class);
    }

    @Test
    void decodeMessage_returnsTypedCandyMessage() throws Exception {
        CandyMessage original = new CandyMessage("gummy-bear", 15.5);
        byte[] body = CandyProtocolCodec.encodeCandyBody(original);

        CandyMessage decoded = (CandyMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void encodeBody_fromFieldsMap_roundTripsWithDecodeMessage() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", "lollipop");
        fields.put("calories", 80.0);

        byte[] body = definition.encodeBody(fields);
        CandyMessage decoded = (CandyMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new CandyMessage("lollipop", 80.0));
    }

    @Test
    void encodeBody_fromProtocolMessageInstance_roundTripsWithDecodeMessage() throws Exception {
        CandyMessage message = new CandyMessage("caramel", 120.0);

        byte[] body = definition.encodeBody((com.example.schemacore.ProtocolMessage) message);
        CandyMessage decoded = (CandyMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void encodeBody_withMissingNameField_throwsIllegalArgumentException() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("calories", 1.0);

        assertThatThrownBy(() -> definition.encodeBody(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeBody_withMissingCaloriesField_throwsIllegalArgumentException() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", "mint");

        assertThatThrownBy(() -> definition.encodeBody(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
