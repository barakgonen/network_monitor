package com.example.binaryserdes;

import com.example.binaryserdes.translators.DynamicStringTranslator;
import com.example.binaryserdes.translators.FixedStringTranslator;
import com.example.binaryserdes.translators.UInt16Translator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTypeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parseToJsonShouldDecodeFieldsInOrder() throws Exception {
        String expectedString = "BG  ";
        Type<Integer> u16 = new Type<>("u16", 2, Integer.class, new UInt16Translator());
        Type<String> name4 = new Type<>("name4", 4, String.class, new FixedStringTranslator(4));

        MessageType mt = MessageType.builder()
                .name("Person")
                .fields(List.of(
                        new MessageField<>("age", u16),
                        new MessageField<>("name", name4)))
                .build();

        ByteBuffer buf = ByteBuffer.allocate(6);
        buf.putShort((short) 30);
        buf.put(expectedString.getBytes(StandardCharsets.US_ASCII));

        String json = mt.parseToJson(buf.array());

        @SuppressWarnings("unchecked")
        Map<String, Object> map = MAPPER.readValue(json, Map.class);

        assertThat(map.get("age")).isEqualTo(30);
        assertThat(map.get("name")).isEqualTo(expectedString);
    }

    @Test
    void toBytesShouldEncodeJsonIntoBytes() throws Exception {
        Type<Integer> u16 = new Type<>("u16", 2, Integer.class, new UInt16Translator());
        Type<String> name4 = new Type<>("name4", 4, String.class, new FixedStringTranslator(4));

        MessageType mt = MessageType.builder()
                .name("Person")
                .fields(List.of(
                        new MessageField<>("age", u16),
                        new MessageField<>("name", name4)))
                .build();

        String json = """
                {
                  "age": 40,
                  "name": "BG"
                }
                """;

        byte[] bytes = mt.toBytes(json);

        String decodedJson = mt.parseToJson(bytes);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = MAPPER.readValue(decodedJson, Map.class);

        assertThat(map.get("age")).isEqualTo(40);
        assertThat(map.get("name")).isEqualTo("BG");
    }

    @Test
    void toBytesShouldFailForVariableLengthField() {
        Type<String> lp = new Type<>("lpStr", -1, String.class, new DynamicStringTranslator());

        MessageType mt = MessageType.builder()
                .name("VarMsg")
                .field(new MessageField<>("comment", lp))
                .build();

        String json = """
                { "comment": "hello" }
                """;

        assertThatThrownBy(() -> mt.toBytes(json)).isInstanceOf(IllegalStateException.class);
    }
}
