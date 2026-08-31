package com.example.schemacore.binaryserdes;

import com.example.schemacore.binaryserdes.translators.Int32Translator;
import com.example.schemacore.binaryserdes.translators.UInt8Translator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArrayTypeTest {

    @Test
    void primitiveElementArray_roundTrips() {
        Type<Integer> uint8 = new Type<>("uint8", 1, Integer.class, new UInt8Translator());
        ArrayType arrayType = new ArrayType("bytes4", uint8, 4);

        assertThat(arrayType.getSizeInBytes()).isEqualTo(4);

        ArrayNode input = JsonNodeFactory.instance.arrayNode();
        input.add(1).add(2).add(3).add(4);

        ByteBuffer buffer = ByteBuffer.allocate(4);
        arrayType.getTranslator().toBytes(input, buffer);
        buffer.flip();

        ArrayNode decoded = (ArrayNode) arrayType.getTranslator().fromBytes(buffer);

        assertThat(decoded.size()).isEqualTo(4);
        for (int i = 0; i < 4; i++) {
            assertThat(decoded.get(i).asInt()).isEqualTo(i + 1);
        }
    }

    @Test
    void recordElementArray_roundTrips() {
        Map<String, Type<?>> pointFields = new LinkedHashMap<>();
        pointFields.put("x", new Type<>("int32", 4, Integer.class, new Int32Translator()));
        pointFields.put("y", new Type<>("int32", 4, Integer.class, new Int32Translator()));
        RecordType pointType = new RecordType("Point", pointFields);

        ArrayType arrayType = new ArrayType("points2", pointType, 2);
        assertThat(arrayType.getSizeInBytes()).isEqualTo(16);

        ArrayNode input = JsonNodeFactory.instance.arrayNode();
        input.addObject().put("x", 1).put("y", 2);
        input.addObject().put("x", 3).put("y", 4);

        ByteBuffer buffer = ByteBuffer.allocate(16);
        arrayType.getTranslator().toBytes(input, buffer);
        buffer.flip();

        ArrayNode decoded = (ArrayNode) arrayType.getTranslator().fromBytes(buffer);

        assertThat(decoded.get(0).get("x").asInt()).isEqualTo(1);
        assertThat(decoded.get(0).get("y").asInt()).isEqualTo(2);
        assertThat(decoded.get(1).get("x").asInt()).isEqualTo(3);
        assertThat(decoded.get(1).get("y").asInt()).isEqualTo(4);
    }

    @Test
    void toBytes_withWrongElementCount_throws() {
        Type<Integer> uint8 = new Type<>("uint8", 1, Integer.class, new UInt8Translator());
        ArrayType arrayType = new ArrayType("bytes4", uint8, 4);

        ArrayNode tooShort = JsonNodeFactory.instance.arrayNode();
        tooShort.add(1).add(2);

        assertThatThrownBy(() -> arrayType.getTranslator().toBytes(tooShort, ByteBuffer.allocate(4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void variableLengthElement_makesArraySizeVariable() {
        Type<String> variableString = new Type<>("string", -1, String.class,
                new com.example.schemacore.binaryserdes.translators.DynamicStringTranslator());
        ArrayType arrayType = new ArrayType("strings3", variableString, 3);

        assertThat(arrayType.getSizeInBytes()).isEqualTo(-1);
    }
}
