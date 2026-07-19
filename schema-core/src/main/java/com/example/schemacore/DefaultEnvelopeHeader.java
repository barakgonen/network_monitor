package com.example.schemacore;

import java.nio.ByteBuffer;

/**
 * Reflective POJO mirroring {@link ProtocolHeaderCodec}'s fixed envelope layout byte-for-byte
 * (opcode:int, sendTimeEpochMillis:long, bodyLength:int, big-endian). This is the default
 * {@code headerType} for interfaces that don't configure a custom one, so existing wire formats
 * keep working unchanged once ingestion becomes per-interface-aware.
 */
public class DefaultEnvelopeHeader {
    private int opcode;
    private long sendTimeEpochMillis;
    private int bodyLength;

    public DefaultEnvelopeHeader() {
    }

    public static DefaultEnvelopeHeader fromByteBuffer(ByteBuffer buffer) {
        DefaultEnvelopeHeader header = new DefaultEnvelopeHeader();
        header.opcode = buffer.getInt();
        header.sendTimeEpochMillis = buffer.getLong();
        header.bodyLength = buffer.getInt();
        return header;
    }

    public void toByteArray(ByteBuffer buffer) {
        buffer.putInt(opcode);
        buffer.putLong(sendTimeEpochMillis);
        buffer.putInt(bodyLength);
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public long getSendTimeEpochMillis() {
        return sendTimeEpochMillis;
    }

    public void setSendTimeEpochMillis(long sendTimeEpochMillis) {
        this.sendTimeEpochMillis = sendTimeEpochMillis;
    }

    public int getBodyLength() {
        return bodyLength;
    }

    public void setBodyLength(int bodyLength) {
        this.bodyLength = bodyLength;
    }
}
