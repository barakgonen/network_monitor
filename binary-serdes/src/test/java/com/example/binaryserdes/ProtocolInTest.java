package com.example.binaryserdes;

import com.example.binaryserdes.translators.FixedStringTranslator;
import com.example.binaryserdes.translators.UInt16Translator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolInTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parseByNameShouldUseRegisteredMessageType() throws Exception {
        Type<Integer> u16 = new Type<>("u16", 2, Integer.class, new UInt16Translator());
        Type<String> name4 = new Type<>("name4", 4, String.class, new FixedStringTranslator(4));

        MessageType mt = MessageType.builder()
                .name("SimpleIn")
                .opcode(1)
                .field(new MessageField<>("value", u16))
                .field(new MessageField<>("name", name4))
                .build();

        ProtocolIn protocolIn = ProtocolIn.create().registerMessage(mt);

        String expectedPayload = "BG  ";
        ByteBuffer buf = ByteBuffer.allocate(6);
        buf.putShort((short) 1234);
        buf.put(expectedPayload.getBytes(StandardCharsets.US_ASCII));
        byte[] bytes = buf.array();

        String json = protocolIn.parse("SimpleIn", bytes);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = MAPPER.readValue(json, Map.class);

        assertThat(map.get("value")).isEqualTo(1234);
        assertThat(map.get("name")).isEqualTo(expectedPayload);
    }

    @Test
    void parseByNameShouldAcceptByteBuffer() throws Exception {
        Type<Integer> u16 = new Type<>("u16", 2, Integer.class, new UInt16Translator());

        MessageType mt = MessageType.builder()
                .name("SimpleIn")
                .field(new MessageField<>("value", u16))
                .build();

        ProtocolIn protocolIn = ProtocolIn.create().registerMessage(mt);

        ByteBuffer buf = ByteBuffer.allocate(2);
        buf.putShort((short) 1234);

        String json = protocolIn.parse("SimpleIn", buf.flip());

        @SuppressWarnings("unchecked")
        Map<String, Object> map = MAPPER.readValue(json, Map.class);

        assertThat(map.get("value")).isEqualTo(1234);
    }

    @Test
    void parseByOpcodeShouldUseRegisteredMessageType() throws Exception {
        Type<Integer> u16 = new Type<>("u16", 2, Integer.class, new UInt16Translator());

        MessageType mt = MessageType.builder()
                .name("SimpleIn")
                .opcode(42)
                .field(new MessageField<>("value", u16))
                .build();

        ProtocolIn protocolIn = ProtocolIn.create().registerMessage(mt);

        ByteBuffer buf = ByteBuffer.allocate(2);
        buf.putShort((short) 777);
        byte[] bytes = buf.array();

        String json = protocolIn.parse(42, bytes);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = MAPPER.readValue(json, Map.class);

        assertThat(map.get("value")).isEqualTo(777);
    }

    @Test
    void parseByOpcodeWithLittleEndianByteOrder_decodesCorrectly() throws Exception {
        Type<Integer> i32 = new Type<>("i32", 4, Integer.class, new com.example.binaryserdes.translators.Int32Translator());

        MessageType mt = MessageType.builder()
                .name("LeMessage")
                .opcode(1)
                .field(new MessageField<>("value", i32))
                .build();

        ProtocolIn protocolIn = ProtocolIn.create().registerMessage(mt);

        ByteBuffer buf = ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putInt(258);

        String json = protocolIn.parse(1, buf.array(), java.nio.ByteOrder.LITTLE_ENDIAN);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = MAPPER.readValue(json, Map.class);

        assertThat(map.get("value")).isEqualTo(258);
    }

    @Test
    void parseUnknownNameShouldThrow() {
        ProtocolIn protocolIn = ProtocolIn.create();
        byte[] bytes = new byte[]{0x01, 0x02};

        assertThatThrownBy(() -> protocolIn.parse("Unknown", bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown inbound message type");
    }

    @Test
    void parseUnknownOpcodeShouldThrow() {
        ProtocolIn protocolIn = ProtocolIn.create();
        byte[] bytes = new byte[]{0x01, 0x02};

        assertThatThrownBy(() -> protocolIn.parse(999, bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown inbound opcode");
    }
}
