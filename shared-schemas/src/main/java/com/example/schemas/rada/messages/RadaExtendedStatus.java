package com.example.schemas.rada.messages;

import com.example.schemacore.ProtocolMessage;
import com.example.schemas.rada.struct.RadaHeader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RadaExtendedStatus implements ProtocolMessage {

    private RadaHeader header = new RadaHeader();
    private double latitude;
    private double longitude;
    private float altitude;
    private float pitch;
    private float roll;
    private float heading;
    private float coverage1Sector1;
    private float coverage1Sector2;
    private float coverage1Radius;

    public RadaExtendedStatus() {
    }

    public RadaExtendedStatus(byte[] message) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(message);
        fromByteArray(byteBuffer);
    }

    public RadaHeader getHeader() {
        return header;
    }

    public void setHeader(RadaHeader header) {
        this.header = header;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float getAltitude() {
        return altitude;
    }

    public void setAltitude(float altitude) {
        this.altitude = altitude;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getRoll() {
        return roll;
    }

    public void setRoll(float roll) {
        this.roll = roll;
    }

    public float getHeading() {
        return heading;
    }

    public void setHeading(float heading) {
        this.heading = heading;
    }

    public float getCoverage1Sector1() {
        return coverage1Sector1;
    }

    public void setCoverage1Sector1(float coverage1Sector1) {
        this.coverage1Sector1 = coverage1Sector1;
    }

    public float getCoverage1Sector2() {
        return coverage1Sector2;
    }

    public void setCoverage1Sector2(float coverage1Sector2) {
        this.coverage1Sector2 = coverage1Sector2;
    }

    public float getCoverage1Radius() {
        return coverage1Radius;
    }

    public void setCoverage1Radius(float coverage1Radius) {
        this.coverage1Radius = coverage1Radius;
    }

    public void toByteArray(ByteBuffer byteBuffer) {
        header.toByteArray(byteBuffer);
        byteBuffer.putDouble(latitude);
        byteBuffer.putDouble(longitude);
        byteBuffer.putFloat(altitude);
        byteBuffer.putFloat(pitch);
        byteBuffer.putFloat(roll);
        byteBuffer.putFloat(heading);
        byteBuffer.putFloat(coverage1Sector1);
        byteBuffer.putFloat(coverage1Sector2);
        byteBuffer.putFloat(coverage1Radius);
    }

    public void fromByteArray(ByteBuffer byteBuffer) {
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        header.fromByteArray(byteBuffer);
        latitude = byteBuffer.getDouble();
        longitude = byteBuffer.getDouble();
        altitude = byteBuffer.getFloat();
        pitch = byteBuffer.getFloat();
        roll = byteBuffer.getFloat();
        heading = byteBuffer.getFloat();
        coverage1Sector1 = byteBuffer.getFloat();
        coverage1Sector2 = byteBuffer.getFloat();
        coverage1Radius = byteBuffer.getFloat();
    }
}
