package com.example.schemas.ping;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PingMessageDefinitionTest {

    private final PingMessageDefinition definition = new PingMessageDefinition();

    @Test
    void interfaceName_and_messageType_and_opcode_and_messageClass_returnExpectedConstants() {
        assertThat(definition.interfaceName()).isEqualTo("Ping Interface");
        assertThat(definition.messageType()).isEqualTo("Ping");
        assertThat(definition.opcode()).isEqualTo(PingOpcodes.PING);
        assertThat(definition.messageClass()).isEqualTo(PingMessage.class);
    }

    @Test
    void decodeMessage_returnsTypedPingMessage() throws Exception {
        byte[] body = PingProtocolCodec.encodePingBody(new PingMessage(7));

        PingMessage decoded = (PingMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new PingMessage(7));
    }

    @Test
    void encodeBody_fromFieldsMap_roundTripsWithDecodeMessage() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sequence", 42);

        byte[] body = definition.encodeBody(fields);
        PingMessage decoded = (PingMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new PingMessage(42));
    }

    @Test
    void encodeBody_fromProtocolMessageInstance_roundTripsWithDecodeMessage() throws Exception {
        PingMessage message = new PingMessage(99);

        byte[] body = definition.encodeBody((com.example.schemacore.ProtocolMessage) message);
        PingMessage decoded = (PingMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void encodeBody_withMissingSequenceField_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> definition.encodeBody(new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
