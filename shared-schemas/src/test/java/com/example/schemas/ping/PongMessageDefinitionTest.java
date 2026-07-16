package com.example.schemas.ping;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PongMessageDefinitionTest {

    private final PongMessageDefinition definition = new PongMessageDefinition();

    @Test
    void interfaceName_and_messageType_and_opcode_and_messageClass_returnExpectedConstants() {
        assertThat(definition.interfaceName()).isEqualTo("Ping Interface");
        assertThat(definition.messageType()).isEqualTo("Pong");
        assertThat(definition.opcode()).isEqualTo(PingOpcodes.PONG);
        assertThat(definition.messageClass()).isEqualTo(PongMessage.class);
    }

    @Test
    void decodeMessage_returnsTypedPongMessage() throws Exception {
        byte[] body = PingProtocolCodec.encodePongBody(new PongMessage(7));

        PongMessage decoded = (PongMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new PongMessage(7));
    }

    @Test
    void encodeBody_fromFieldsMap_roundTripsWithDecodeMessage() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sequence", 42);

        byte[] body = definition.encodeBody(fields);
        PongMessage decoded = (PongMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new PongMessage(42));
    }

    @Test
    void encodeBody_fromProtocolMessageInstance_roundTripsWithDecodeMessage() throws Exception {
        PongMessage message = new PongMessage(99);

        byte[] body = definition.encodeBody((com.example.schemacore.ProtocolMessage) message);
        PongMessage decoded = (PongMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void encodeBody_withMissingSequenceField_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> definition.encodeBody(new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
