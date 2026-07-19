package com.example.schemacore;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReflectiveStructCodecTest {

    record FixedRecord(int id, short flags) {
        static FixedRecord fromByteBuffer(ByteBuffer buffer) {
            return new FixedRecord(buffer.getInt(), buffer.getShort());
        }

        void toByteArray(ByteBuffer buffer) {
            buffer.putInt(id);
            buffer.putShort(flags);
        }
    }

    static class MutableStruct {
        private int value;

        public MutableStruct() {
        }

        public MutableStruct(byte[] payload) {
            fromByteArray(ByteBuffer.wrap(payload));
        }

        void fromByteArray(ByteBuffer buffer) {
            value = buffer.getInt();
        }

        void toByteArray(ByteBuffer buffer) {
            buffer.putInt(value);
        }

        int getValue() {
            return value;
        }

        void setValue(int value) {
            this.value = value;
        }
    }

    static class SelfSizingMessage {
        private String text;

        public SelfSizingMessage() {
        }

        byte[] toByteArray() {
            byte[] textBytes = text.getBytes();
            ByteBuffer buffer = ByteBuffer.allocate(4 + textBytes.length);
            buffer.putInt(textBytes.length);
            buffer.put(textBytes);
            return buffer.array();
        }

        static SelfSizingMessage fromByteBuffer(ByteBuffer buffer) {
            int length = buffer.getInt();
            byte[] textBytes = new byte[length];
            buffer.get(textBytes);
            SelfSizingMessage message = new SelfSizingMessage();
            message.text = new String(textBytes);
            return message;
        }
    }

    @Test
    void decodesViaStaticFromByteBufferFactory_forRecords() {
        ByteBuffer buffer = ByteBuffer.allocate(6);
        buffer.putInt(7);
        buffer.putShort((short) 3);

        FixedRecord decoded = ReflectiveStructCodec.decode(FixedRecord.class, buffer.array());

        assertThat(decoded).isEqualTo(new FixedRecord(7, (short) 3));
    }

    @Test
    void encodesViaSizedToByteArray_forRecords() {
        FixedRecord record = new FixedRecord(7, (short) 3);
        byte[] encoded = ReflectiveStructCodec.encode(record);

        assertThat(ReflectiveStructCodec.decode(FixedRecord.class, encoded)).isEqualTo(record);
    }

    @Test
    void decodesViaByteArrayConstructor_forMutableStructs() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(42);

        MutableStruct decoded = ReflectiveStructCodec.decode(MutableStruct.class, buffer.array());

        assertThat(decoded.getValue()).isEqualTo(42);
    }

    @Test
    void encodesViaSizedToByteArray_forMutableStructs() {
        MutableStruct struct = new MutableStruct();
        struct.setValue(99);

        byte[] encoded = ReflectiveStructCodec.encode(struct);

        assertThat(ReflectiveStructCodec.decode(MutableStruct.class, encoded).getValue()).isEqualTo(99);
    }

    @Test
    void prefersNoArgToByteArray_forSelfSizingMessages() {
        SelfSizingMessage message = new SelfSizingMessage();
        message.text = "hello";

        byte[] encoded = ReflectiveStructCodec.encode(message);
        SelfSizingMessage decoded = ReflectiveStructCodec.decode(SelfSizingMessage.class, encoded);

        assertThat(decoded.text).isEqualTo("hello");
    }

    static class UnsupportedType {
    }

    @Test
    void decode_failsWithClearMessage_whenNoSupportedDecoder() {
        assertThatThrownBy(() -> ReflectiveStructCodec.decode(UnsupportedType.class, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not expose a supported decoder");
    }

    @Test
    void encode_failsWithClearMessage_whenNoSupportedEncoder() {
        assertThatThrownBy(() -> ReflectiveStructCodec.encode(new UnsupportedType()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not expose a supported encoder");
    }
}
