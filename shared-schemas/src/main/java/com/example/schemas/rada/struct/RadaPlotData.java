package com.example.schemas.rada.struct;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RadaPlotData {

    private float range;
    private float rangSTD;
    private float dopplerVelocity;
    private float dopplerVelocitySTD;
    private float azimuth;
    private float azimuthSTD;
    private float elevation;
    private float elevationSTD;
    private float snr;
    private float rcs;
    private int reserved;

    public RadaPlotData() {
    }

    public RadaPlotData(byte[] message) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(message);
        fromByteArray(byteBuffer);
    }

    public float getRange() {
        return range;
    }

    public void setRange(float range) {
        this.range = range;
    }

    public float getRangSTD() {
        return rangSTD;
    }

    public void setRangSTD(float rangSTD) {
        this.rangSTD = rangSTD;
    }

    public float getDopplerVelocity() {
        return dopplerVelocity;
    }

    public void setDopplerVelocity(float dopplerVelocity) {
        this.dopplerVelocity = dopplerVelocity;
    }

    public float getDopplerVelocitySTD() {
        return dopplerVelocitySTD;
    }

    public void setDopplerVelocitySTD(float dopplerVelocitySTD) {
        this.dopplerVelocitySTD = dopplerVelocitySTD;
    }

    public float getAzimuth() {
        return azimuth;
    }

    public void setAzimuth(float azimuth) {
        this.azimuth = azimuth;
    }

    public float getAzimuthSTD() {
        return azimuthSTD;
    }

    public void setAzimuthSTD(float azimuthSTD) {
        this.azimuthSTD = azimuthSTD;
    }

    public float getElevation() {
        return elevation;
    }

    public void setElevation(float elevation) {
        this.elevation = elevation;
    }

    public float getElevationSTD() {
        return elevationSTD;
    }

    public void setElevationSTD(float elevationSTD) {
        this.elevationSTD = elevationSTD;
    }

    public float getSnr() {
        return snr;
    }

    public void setSnr(float snr) {
        this.snr = snr;
    }

    public float getRcs() {
        return rcs;
    }

    public void setRcs(float rcs) {
        this.rcs = rcs;
    }

    public long getReserved() {
        return Integer.toUnsignedLong(reserved);
    }

    public void setReserved(long reserved) {
        this.reserved = (int) reserved;
    }

    public void toByteArray(ByteBuffer byteBuffer) {
        byteBuffer.putFloat(range);
        byteBuffer.putFloat(rangSTD);
        byteBuffer.putFloat(dopplerVelocity);
        byteBuffer.putFloat(dopplerVelocitySTD);
        byteBuffer.putFloat(azimuth);
        byteBuffer.putFloat(azimuthSTD);
        byteBuffer.putFloat(elevation);
        byteBuffer.putFloat(elevationSTD);
        byteBuffer.putFloat(snr);
        byteBuffer.putFloat(rcs);
        byteBuffer.putInt(reserved);
    }

    public void fromByteArray(ByteBuffer byteBuffer) {
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        range = byteBuffer.getFloat();
        rangSTD = byteBuffer.getFloat();
        dopplerVelocity = byteBuffer.getFloat();
        dopplerVelocitySTD = byteBuffer.getFloat();
        azimuth = byteBuffer.getFloat();
        azimuthSTD = byteBuffer.getFloat();
        elevation = byteBuffer.getFloat();
        elevationSTD = byteBuffer.getFloat();
        snr = byteBuffer.getFloat();
        rcs = byteBuffer.getFloat();
        reserved = byteBuffer.getInt();
    }
}
