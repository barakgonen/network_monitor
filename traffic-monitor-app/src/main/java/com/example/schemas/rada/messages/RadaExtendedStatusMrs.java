package com.example.schemas.rada.messages;

import com.example.schemacore.ProtocolMessage;
import com.example.schemas.rada.enums.FavoriteColor;
import com.example.schemas.rada.struct.RadaHeader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RadaExtendedStatusMrs implements ProtocolMessage {

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
    private float coverage2Sector1;
    private float coverage2Sector2;
    private float coverage2Radius;
    private float coverage3Sector1;
    private FavoriteColor favoriteColor;
    private float coverage3Sector2;
    private float coverage3Radius;
    private float coverage4Sector1;
    private float coverage4Sector2;
    private float coverage4Radius;

    public RadaExtendedStatusMrs() {
    }

    public RadaExtendedStatusMrs(byte[] message) {
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

    public float getCoverage2Sector1() {
        return coverage2Sector1;
    }

    public void setCoverage2Sector1(float coverage2Sector1) {
        this.coverage2Sector1 = coverage2Sector1;
    }

    public float getCoverage2Sector2() {
        return coverage2Sector2;
    }

    public void setCoverage2Sector2(float coverage2Sector2) {
        this.coverage2Sector2 = coverage2Sector2;
    }

    public float getCoverage2Radius() {
        return coverage2Radius;
    }

    public void setCoverage2Radius(float coverage2Radius) {
        this.coverage2Radius = coverage2Radius;
    }

    public float getCoverage3Sector1() {
        return coverage3Sector1;
    }

    public void setCoverage3Sector1(float coverage3Sector1) {
        this.coverage3Sector1 = coverage3Sector1;
    }

    public FavoriteColor getFavoriteColor() {
        return favoriteColor;
    }

    public void setFavoriteColor(FavoriteColor favoriteColor) {
        this.favoriteColor = favoriteColor;
    }

    public float getCoverage3Sector2() {
        return coverage3Sector2;
    }

    public void setCoverage3Sector2(float coverage3Sector2) {
        this.coverage3Sector2 = coverage3Sector2;
    }

    public float getCoverage3Radius() {
        return coverage3Radius;
    }

    public void setCoverage3Radius(float coverage3Radius) {
        this.coverage3Radius = coverage3Radius;
    }

    public float getCoverage4Sector1() {
        return coverage4Sector1;
    }

    public void setCoverage4Sector1(float coverage4Sector1) {
        this.coverage4Sector1 = coverage4Sector1;
    }

    public float getCoverage4Sector2() {
        return coverage4Sector2;
    }

    public void setCoverage4Sector2(float coverage4Sector2) {
        this.coverage4Sector2 = coverage4Sector2;
    }

    public float getCoverage4Radius() {
        return coverage4Radius;
    }

    public void setCoverage4Radius(float coverage4Radius) {
        this.coverage4Radius = coverage4Radius;
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
        byteBuffer.putFloat(coverage2Sector1);
        byteBuffer.putFloat(coverage2Sector2);
        byteBuffer.putFloat(coverage2Radius);
        byteBuffer.putFloat(coverage3Sector1);
        byteBuffer.putInt(favoriteColor.ordinal());
        byteBuffer.putFloat(coverage3Sector2);
        byteBuffer.putFloat(coverage3Radius);
        byteBuffer.putFloat(coverage4Sector1);
        byteBuffer.putFloat(coverage4Sector2);
        byteBuffer.putFloat(coverage4Radius);
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
        coverage2Sector1 = byteBuffer.getFloat();
        coverage2Sector2 = byteBuffer.getFloat();
        coverage2Radius = byteBuffer.getFloat();
        coverage3Sector1 = byteBuffer.getFloat();
        favoriteColor = FavoriteColor.values()[byteBuffer.getInt()];
        coverage3Sector2 = byteBuffer.getFloat();
        coverage3Radius = byteBuffer.getFloat();
        coverage4Sector1 = byteBuffer.getFloat();
        coverage4Sector2 = byteBuffer.getFloat();
        coverage4Radius = byteBuffer.getFloat();
    }
}
