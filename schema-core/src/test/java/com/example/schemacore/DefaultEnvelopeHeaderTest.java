package com.example.schemacore;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultEnvelopeHeaderTest {

    @Test
    void toByteArray_matchesProtocolHeaderCodecEncodeMessageHeaderBytes() {
        byte[] body = {1, 2, 3, 4, 5};
        byte[] expected = ProtocolHeaderCodec.encodeMessage(42, 1_700_000_000_000L, body);

        DefaultEnvelopeHeader header = new DefaultEnvelopeHeader();
        header.setOpcode(42);
        header.setSendTimeEpochMillis(1_700_000_000_000L);
        header.setBodyLength(body.length);

        ByteBuffer buffer = ByteBuffer.allocate(ProtocolHeaderCodec.HEADER_SIZE_BYTES);
        header.toByteArray(buffer);

        byte[] expectedHeaderOnly = new byte[ProtocolHeaderCodec.HEADER_SIZE_BYTES];
        System.arraycopy(expected, 0, expectedHeaderOnly, 0, expectedHeaderOnly.length);

        assertThat(buffer.array()).isEqualTo(expectedHeaderOnly);
    }

    @Test
    void fromByteBuffer_roundTripsAgainstProtocolHeaderCodecDecodeHeader() {
        byte[] body = {9, 8, 7};
        byte[] wire = ProtocolHeaderCodec.encodeMessage(7, 123L, body);

        ProtocolHeader viaCodec = ProtocolHeaderCodec.decodeHeader(ByteBuffer.wrap(wire));
        DefaultEnvelopeHeader viaReflective = DefaultEnvelopeHeader.fromByteBuffer(ByteBuffer.wrap(wire));

        assertThat(viaReflective.getOpcode()).isEqualTo(viaCodec.opcode());
        assertThat(viaReflective.getSendTimeEpochMillis()).isEqualTo(viaCodec.sendTimeEpochMillis());
        assertThat(viaReflective.getBodyLength()).isEqualTo(viaCodec.bodyLength());
    }

    @Test
    void encodeViaReflectiveStructCodec_producesSameBytesAsHandWrittenToByteArray() {
        DefaultEnvelopeHeader header = new DefaultEnvelopeHeader();
        header.setOpcode(11);
        header.setSendTimeEpochMillis(999L);
        header.setBodyLength(3);

        byte[] viaReflection = ReflectiveStructCodec.encode(header);

        ByteBuffer expected = ByteBuffer.allocate(ProtocolHeaderCodec.HEADER_SIZE_BYTES);
        header.toByteArray(expected);

        assertThat(viaReflection).isEqualTo(expected.array());
    }
}
