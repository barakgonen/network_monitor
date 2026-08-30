package com.example.schemacore.binaryserdes;

import com.example.schemacore.binaryserdes.translators.FixedStringTranslator;
import com.example.schemacore.binaryserdes.translators.UInt16Translator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolOutTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void encodeByNameShouldProduceBytesAndBeDecodable() throws Exception {
        Type<Integer> u16 = new Type<>("u16", 2, Integer.class, new UInt16Translator());
        Type<String> name4 = new Type<>("name4", 4, String.class, new FixedStringTranslator(4));

        MessageType mt = MessageType.builder()
                .name("OutMsg")
                .opcode(10)
                .field(new MessageField<>("age", u16))
                .field(new MessageField<>("name", name4))
                .build();

        ProtocolOut protocolOut = ProtocolOut.create().registerMessage(mt);

        String json = """
                {
                  "age": 25,
                  "name": "BG"
                }
                """;

        byte[] bytes = protocolOut.encode("OutMsg", json);
        assertThat(bytes).isNotNull();
        assertThat(bytes).hasSize(6);

        String decodedJson = mt.parseToJson(bytes);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = MAPPER.readValue(decodedJson, Map.class);

        assertThat(map.get("age")).isEqualTo(25);
        assertThat(map.get("name")).isEqualTo("BG");
    }

    @Test
    void encodeByOpcodeShouldProduceBytesAndBeDecodable() throws Exception {
        Type<Integer> u16 = new Type<>("u16", 2, Integer.class, new UInt16Translator());
        Type<String> name4 = new Type<>("name4", 4, String.class, new FixedStringTranslator(4));

        MessageType mt = MessageType.builder()
                .name("OutMsg")
                .opcode(7)
                .field(new MessageField<>("age", u16))
                .field(new MessageField<>("name", name4))
                .build();

        ProtocolOut protocolOut = ProtocolOut.create().registerMessage(mt);

        String json = """
                {
                  "age": 30,
                  "name": "BG"
                }
                """;

        byte[] bytes = protocolOut.encode(7, json);

        String decodedJson = mt.parseToJson(bytes);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = MAPPER.readValue(decodedJson, Map.class);

        assertThat(map.get("age")).isEqualTo(30);
        assertThat(map.get("name")).isEqualTo("BG");
    }

    @Test
    void encodeUnknownNameShouldThrow() {
        ProtocolOut protocolOut = ProtocolOut.create();

        assertThatThrownBy(() -> protocolOut.encode("Unknown", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown outbound message type");
    }

    @Test
    void encodeUnknownOpcodeShouldThrow() {
        ProtocolOut protocolOut = ProtocolOut.create();

        assertThatThrownBy(() -> protocolOut.encode(123, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown outbound opcode");
    }
}
