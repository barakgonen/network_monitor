package com.example.schemacore.reflect;

import com.example.binaryserdes.envelope.ProtocolHeaderCodec;
import com.example.schemacore.envelope.DefaultEnvelopeHeader;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectiveHeaderDecoderTest {

    @Test
    void headerSize_matchesStructSize() {
        ReflectiveHeaderDecoder decoder = new ReflectiveHeaderDecoder(DefaultEnvelopeHeader.class);

        assertThat(decoder.headerSize()).isEqualTo(ProtocolHeaderCodec.HEADER_SIZE_BYTES);
    }

    @Test
    void decode_extractsFieldsFromHeaderBytes() throws Exception {
        ReflectiveHeaderDecoder decoder = new ReflectiveHeaderDecoder(DefaultEnvelopeHeader.class);

        byte[] wireBytes = ProtocolHeaderCodec.encodeMessage(1001, 12345L, new byte[0]);
        byte[] headerBytes = ByteBuffer.allocate(decoder.headerSize())
                .put(wireBytes, 0, decoder.headerSize())
                .array();

        Map<String, Object> fields = decoder.decode(headerBytes, ByteOrder.BIG_ENDIAN);

        assertThat(fields.get("opcode")).isEqualTo(1001);
        assertThat(fields.get("sendTimeEpochMillis")).isEqualTo(12345L);
    }
}
