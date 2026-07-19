package com.example.schemas.rada.struct;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RadaHeader {

    private int msgCounter;
    private int msgType;
    private byte icdVersion;
    private byte reserved1;
    private byte reserved2;
    private byte reserved3;
    private int msgSize;

    public RadaHeader() {
    }

    public RadaHeader(byte[] msg) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(msg);
        fromByteArray(byteBuffer);
    }

    public long getMsgCounter() {
        return Integer.toUnsignedLong(msgCounter);
    }

    public void setMsgCounter(long msgCounter) {
        this.msgCounter = (int) msgCounter;
    }

    public long getMsgType() {
        return Integer.toUnsignedLong(msgType);
    }

    public void setMsgType(long msgType) {
        this.msgType = (int) msgType;
    }

    public long getMsgSize() {
        return Integer.toUnsignedLong(msgSize);
    }

    public void setMsgSize(long msgSize) {
        this.msgSize = (int) msgSize;
    }

    public short getIcdVersion() {
        return (short) (icdVersion & 0xFF);
    }

    public void setIcdVersion(short icdVersion) {
        this.icdVersion = (byte) icdVersion;
    }

    public short getReserved1() {
        return (short) (reserved1 & 0xFF);
    }

    public void setReserved1(short reserved1) {
        this.reserved1 = (byte) reserved1;
    }

    public short getReserved2() {
        return (short) (reserved2 & 0xFF);
    }

    public void setReserved2(short reserved2) {
        this.reserved2 = (byte) reserved2;
    }

    public short getReserved3() {
        return (short) (reserved3 & 0xFF);
    }

    public void setReserved3(short reserved3) {
        this.reserved3 = (byte) reserved3;
    }

    public void toByteArray(ByteBuffer byteBuffer) {
        byteBuffer.putInt(msgCounter);
        byteBuffer.putInt(msgType);
        byteBuffer.put(icdVersion);
        byteBuffer.put(reserved1);
        byteBuffer.put(reserved2);
        byteBuffer.put(reserved3);
        byteBuffer.putInt(msgSize);
    }

    public void fromByteArray(ByteBuffer byteBuffer) {
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        msgCounter = byteBuffer.getInt();
        msgType = byteBuffer.getInt();
        icdVersion = byteBuffer.get();
        reserved1 = byteBuffer.get();
        reserved2 = byteBuffer.get();
        reserved3 = byteBuffer.get();
        msgSize = byteBuffer.getInt();
    }
}
