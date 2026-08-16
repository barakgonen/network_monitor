package com.example.schemacore.reflect;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

    static class OrderAwareMutableStruct {
        private int value;
        private ByteOrder decodedWith;

        public OrderAwareMutableStruct() {
        }

        public OrderAwareMutableStruct(byte[] payload, ByteOrder byteOrder) {
            ByteBuffer buffer = ByteBuffer.wrap(payload).order(byteOrder);
            value = buffer.getInt();
            decodedWith = byteOrder;
        }

        public OrderAwareMutableStruct(byte[] payload) {
            throw new AssertionError("order-aware constructor should be preferred");
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

        ByteOrder getDecodedWith() {
            return decodedWith;
        }
    }

    static class OrderAwareSelfSizingMessage {
        private int value;

        public OrderAwareSelfSizingMessage() {
        }

        byte[] toByteArray(ByteOrder byteOrder) {
            ByteBuffer buffer = ByteBuffer.allocate(4).order(byteOrder);
            buffer.putInt(value);
            return buffer.array();
        }

        byte[] toByteArray() {
            throw new AssertionError("order-aware encode should be preferred");
        }

        static OrderAwareSelfSizingMessage fromByteBuffer(ByteBuffer buffer) {
            OrderAwareSelfSizingMessage message = new OrderAwareSelfSizingMessage();
            message.value = buffer.getInt();
            return message;
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

    @Test
    void fromByteBufferDecode_honorsRequestedByteOrder() {
        ByteBuffer buffer = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(7);
        buffer.putShort((short) 3);

        FixedRecord decoded = ReflectiveStructCodec.decode(FixedRecord.class, buffer.array(), ByteOrder.LITTLE_ENDIAN);

        assertThat(decoded).isEqualTo(new FixedRecord(7, (short) 3));
    }

    @Test
    void sizedToByteArrayEncode_honorsRequestedByteOrder() {
        FixedRecord record = new FixedRecord(7, (short) 3);

        byte[] encoded = ReflectiveStructCodec.encode(record, ByteOrder.LITTLE_ENDIAN);

        assertThat(ReflectiveStructCodec.decode(FixedRecord.class, encoded, ByteOrder.LITTLE_ENDIAN)).isEqualTo(record);
        assertThat(ReflectiveStructCodec.decode(FixedRecord.class, encoded, ByteOrder.BIG_ENDIAN)).isNotEqualTo(record);
    }

    @Test
    void prefersByteOrderAwareConstructor_forMutableStructs() {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(42);

        OrderAwareMutableStruct decoded =
                ReflectiveStructCodec.decode(OrderAwareMutableStruct.class, buffer.array(), ByteOrder.LITTLE_ENDIAN);

        assertThat(decoded.getValue()).isEqualTo(42);
        assertThat(decoded.getDecodedWith()).isEqualTo(ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    void prefersByteOrderAwareToByteArray_forSelfSizingMessages() {
        OrderAwareSelfSizingMessage message = new OrderAwareSelfSizingMessage();
        message.setValue(123);

        byte[] encoded = ReflectiveStructCodec.encode(message, ByteOrder.LITTLE_ENDIAN);
        OrderAwareSelfSizingMessage decoded =
                ReflectiveStructCodec.decode(OrderAwareSelfSizingMessage.class, encoded, ByteOrder.LITTLE_ENDIAN);

        assertThat(decoded.getValue()).isEqualTo(123);
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
