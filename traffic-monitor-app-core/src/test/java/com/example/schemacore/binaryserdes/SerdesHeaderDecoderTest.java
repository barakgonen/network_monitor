package com.example.schemacore.binaryserdes;

import com.example.schemacore.binaryserdes.translators.Int32Translator;
import com.example.schemacore.binaryserdes.translators.UInt8Translator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SerdesHeaderDecoderTest {

    private RecordType headerType() {
        Map<String, Type<?>> fields = new LinkedHashMap<>();
        fields.put("msgCounter", new Type<>("int32", 4, Integer.class, new Int32Translator()));
        fields.put("msgType", new Type<>("int32", 4, Integer.class, new Int32Translator()));
        fields.put("icdVersion", new Type<>("uint8", 1, Integer.class, new UInt8Translator()));
        return new RecordType("TestHeader", fields);
    }

    @Test
    void headerSize_matchesRecordSize() {
        SerdesHeaderDecoder decoder = new SerdesHeaderDecoder(headerType());

        assertThat(decoder.headerSize()).isEqualTo(9);
    }

    @Test
    void decode_extractsFieldsFromHeaderBytes() {
        SerdesHeaderDecoder decoder = new SerdesHeaderDecoder(headerType());

        ByteBuffer buffer = ByteBuffer.allocate(9);
        buffer.putInt(7);
        buffer.putInt(3);
        buffer.put((byte) 1);

        Map<String, Object> fields = decoder.decode(buffer.array(), ByteOrder.BIG_ENDIAN);

        assertThat(fields.get("msgCounter")).isEqualTo(7);
        assertThat(fields.get("msgType")).isEqualTo(3);
        assertThat(fields.get("icdVersion")).isEqualTo(1);
    }

    @Test
    void decode_respectsLittleEndianByteOrder() {
        SerdesHeaderDecoder decoder = new SerdesHeaderDecoder(headerType());

        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(7);
        buffer.putInt(3);
        buffer.put((byte) 1);

        Map<String, Object> fields = decoder.decode(buffer.array(), ByteOrder.LITTLE_ENDIAN);

        assertThat(fields.get("msgType")).isEqualTo(3);
    }
}
