package com.example.schemas.rada.messages;

import com.example.schemas.BaseStruct;
import com.example.schemas.rada.struct.RadaHeader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RadaStatus implements BaseStruct {

    private RadaHeader header = new RadaHeader();
    private int radarSoftwareVersion = 0;
    private int recordingState = 0;
    private int workingMode = 0;
    private short statusFlags = 0;
    private short remainingRecordingSpace = 0;
    private int bitStatus = 0;
    private short manufacturerData = 0;

    public RadaStatus() {
    }

    public RadaStatus(byte[] message) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(message);
        fromByteArray(byteBuffer);
    }

    public RadaHeader getHeader() {
        return header;
    }

    public void setHeader(RadaHeader header) {
        this.header = header;
    }

    public int getStatusFlags() {
        return statusFlags & 0xFFFF;
    }

    public void setStatusFlags(int statusFlags) {
        this.statusFlags = (short) statusFlags;
    }

    public int getRemainingRecordingSpace() {
        return remainingRecordingSpace & 0xFFFF;
    }

    public void setRemainingRecordingSpace(int remainingRecordingSpace) {
        this.remainingRecordingSpace = (short) remainingRecordingSpace;
    }

    public int getManufacturerData() {
        return manufacturerData & 0xFFFF;
    }

    public void setManufacturerData(int manufacturerData) {
        this.manufacturerData = (short) manufacturerData;
    }

    public long getRadarSoftwareVersion() {
        return Integer.toUnsignedLong(radarSoftwareVersion);
    }

    public void setRadarSoftwareVersion(long radarSoftwareVersion) {
        this.radarSoftwareVersion = (int) radarSoftwareVersion;
    }

    public long getRecordingState() {
        return Integer.toUnsignedLong(recordingState);
    }

    public void setRecordingState(long recordingState) {
        this.recordingState = (int) recordingState;
    }

    public long getWorkingMode() {
        return Integer.toUnsignedLong(workingMode);
    }

    public void setWorkingMode(long workingMode) {
        this.workingMode = (int) workingMode;
    }

    public long getBitStatus() {
        return Integer.toUnsignedLong(bitStatus);
    }

    public void setBitStatus(long bitStatus) {
        this.bitStatus = (int) bitStatus;
    }

    @Override
    public void toByteArray(ByteBuffer byteBuffer) {
        header.toByteArray(byteBuffer);
        byteBuffer.putInt(radarSoftwareVersion);
        byteBuffer.putInt(recordingState);
        byteBuffer.putInt(workingMode);
        byteBuffer.putShort(statusFlags);
        byteBuffer.putShort(remainingRecordingSpace);
        byteBuffer.putInt(bitStatus);
        byteBuffer.putShort(manufacturerData);
    }

    @Override
    public void fromByteArray(ByteBuffer byteBuffer) {
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        header.fromByteArray(byteBuffer);
        radarSoftwareVersion = byteBuffer.getInt();
        recordingState = byteBuffer.getInt();
        workingMode = byteBuffer.getInt();
        statusFlags = byteBuffer.getShort();
        remainingRecordingSpace = byteBuffer.getShort();
        bitStatus = byteBuffer.getInt();
        manufacturerData = byteBuffer.getShort();
    }
}

