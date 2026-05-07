package com.example.schemas.rada.struct;

import com.example.schemas.BaseStruct;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RadaTrackData implements BaseStruct {

    private int id = 0;
    private int type = 0;
    private byte[] reserved = new byte[40];
    private double latitudeRad;
    private double longtitudeRad;
    private float altitudeM;
    private float dopplerVelocity;
    private float radarCrossSection;
    private byte[] reserved2 = new byte[44];
    private short statusFlags; //Unsigned16
    private byte[] reserved3 = new byte[10];
    private int associatedPlots;
    private float timeSinceLastAssociation;
    private float age;
    private float posX;
    private float posY;
    private float posZ;
    private float velocityX;
    private float velocityY;
    private float velocityZ;
    private float posErrorX;
    private float posErrorY;
    private float posErrorZ;
    private float velocityErrorX;
    private float velocityErrorY;
    private float velocityErrorZ;
    private float testParam0;
    private float testParam1;
    private float testParam2;
    private float testParam3;
    private float testParam4;
    private float testParam5;
    private float testParam6;
    private float testParam7;
    private float testParam8;
    private float testParam9;
    public RadaTrackData() {
    }

    public long getId() {
        return Integer.toUnsignedLong(id);
    }

    public void setId(long id) {
        this.id = (int) id;
    }

    public long getType() {
        return Integer.toUnsignedLong(type);
    }

    public void setType(long type) {
        this.type = (int) type;
    }

    public long getAssociatedPlots() {
        return Integer.toUnsignedLong(associatedPlots);
    }

    public void setAssociatedPlots(long associatedPlots) {
        this.associatedPlots = (int) associatedPlots;
    }

    public int getStatusFlags() {
        return statusFlags;
    }

    public void setStatusFlags(short statusFlags) {
        this.statusFlags = statusFlags;
    }

    public float getTimeSinceLastAssociation() {
        return timeSinceLastAssociation;
    }

    public void setTimeSinceLastAssociation(float timeSinceLastAssociation) {
        this.timeSinceLastAssociation = timeSinceLastAssociation;
    }

    public float getAge() {
        return age;
    }

    public void setAge(float age) {
        this.age = age;
    }

    public float getPosX() {
        return posX;
    }

    public void setPosX(float posX) {
        this.posX = posX;
    }

    public float getPosY() {
        return posY;
    }

    public void setPosY(float posY) {
        this.posY = posY;
    }

    public float getPosZ() {
        return posZ;
    }

    public void setPosZ(float posZ) {
        this.posZ = posZ;
    }

    public float getVelocityX() {
        return velocityX;
    }

    public void setVelocityX(float velocityX) {
        this.velocityX = velocityX;
    }

    public float getVelocityY() {
        return velocityY;
    }

    public void setVelocityY(float velocityY) {
        this.velocityY = velocityY;
    }

    public float getVelocityZ() {
        return velocityZ;
    }

    public void setVelocityZ(float velocityZ) {
        this.velocityZ = velocityZ;
    }

    public float getPosErrorX() {
        return posErrorX;
    }

    public void setPosErrorX(float posErrorX) {
        this.posErrorX = posErrorX;
    }

    public float getPosErrorY() {
        return posErrorY;
    }

    public void setPosErrorY(float posErrorY) {
        this.posErrorY = posErrorY;
    }

    public float getPosErrorZ() {
        return posErrorZ;
    }

    public void setPosErrorZ(float posErrorZ) {
        this.posErrorZ = posErrorZ;
    }

    public float getVelocityErrorX() {
        return velocityErrorX;
    }

    public void setVelocityErrorX(float velocityErrorX) {
        this.velocityErrorX = velocityErrorX;
    }

    public float getVelocityErrorY() {
        return velocityErrorY;
    }

    public void setVelocityErrorY(float velocityErrorY) {
        this.velocityErrorY = velocityErrorY;
    }

    public float getVelocityErrorZ() {
        return velocityErrorZ;
    }

    public void setVelocityErrorZ(float velocityErrorZ) {
        this.velocityErrorZ = velocityErrorZ;
    }

    public float getTestParam0() {
        return testParam0;
    }

    public void setTestParam0(float testParam0) {
        this.testParam0 = testParam0;
    }

    public float getTestParam1() {
        return testParam1;
    }

    public void setTestParam1(float testParam1) {
        this.testParam1 = testParam1;
    }

    public float getTestParam2() {
        return testParam2;
    }

    public void setTestParam2(float testParam2) {
        this.testParam2 = testParam2;
    }

    public float getTestParam3() {
        return testParam3;
    }

    public void setTestParam3(float testParam3) {
        this.testParam3 = testParam3;
    }

    public float getTestParam4() {
        return testParam4;
    }

    public void setTestParam4(float testParam4) {
        this.testParam4 = testParam4;
    }

    public float getTestParam5() {
        return testParam5;
    }

    public void setTestParam5(float testParam5) {
        this.testParam5 = testParam5;
    }

    public float getTestParam6() {
        return testParam6;
    }

    public void setTestParam6(float testParam6) {
        this.testParam6 = testParam6;
    }

    public float getTestParam7() {
        return testParam7;
    }

    public void setTestParam7(float testParam7) {
        this.testParam7 = testParam7;
    }

    public float getTestParam8() {
        return testParam8;
    }

    public void setTestParam8(float testParam8) {
        this.testParam8 = testParam8;
    }

    public float getTestParam9() {
        return testParam9;
    }

    public void setTestParam9(float testParam9) {
        this.testParam9 = testParam9;
    }

    public double getLatitudeRad() {
        return latitudeRad;
    }

    public void setLatitudeRad(double latitudeRad) {
        this.latitudeRad = latitudeRad;
    }

    public double getLongtitudeRad() {
        return longtitudeRad;
    }

    public void setLongtitudeRad(double longtitudeRad) {
        this.longtitudeRad = longtitudeRad;
    }

    public float getAltitudeM() {
        return altitudeM;
    }

    public void setAltitudeM(float altitudeM) {
        this.altitudeM = altitudeM;
    }

    public float getDopplerVelocity() {
        return dopplerVelocity;
    }

    public void setDopplerVelocity(float dopplerVelocity) {
        this.dopplerVelocity = dopplerVelocity;
    }

    public float getRadarCrossSection() {
        return radarCrossSection;
    }

    public void setRadarCrossSection(float radarCrossSection) {
        this.radarCrossSection = radarCrossSection;
    }

    public short[] getReserved() {
        short[] res = new short[this.reserved.length];
        for (int i = 0; i < this.reserved.length; i++) {
            res[i] = (short) (this.reserved[i] & 0xFF);
        }
        return res;
    }

    public void setReserved(short[] reserved) {
        byte[] res = new byte[reserved.length];
        for (int i = 0; i < reserved.length; i++) {
            res[i] = (byte) reserved[i];
        }
        this.reserved = res;
    }

    public short[] getReserved2() {
        short[] res = new short[this.reserved2.length];
        for (int i = 0; i < this.reserved2.length; i++) {
            res[i] = (short) (this.reserved2[i] & 0xFF);
        }
        return res;
    }

    public void setReserved2(short[] reserved2) {
        byte[] res = new byte[reserved2.length];
        for (int i = 0; i < reserved2.length; i++) {
            res[i] = (byte) reserved2[i];
        }
        this.reserved2 = res;
    }

    public short[] getReserved3() {
        short[] res = new short[this.reserved3.length];
        for (int i = 0; i < this.reserved3.length; i++) {
            res[i] = (short) (this.reserved3[i] & 0xFF);
        }
        return res;
    }

    public void setReserved3(short[] reserved3) {
        byte[] res = new byte[reserved3.length];
        for (int i = 0; i < reserved3.length; i++) {
            res[i] = (byte) reserved3[i];
        }
        this.reserved3 = res;
    }

    @Override
    public void toByteArray(ByteBuffer byteBuffer) {
        byteBuffer.putInt(id);
        byteBuffer.putInt(type);
        for (int i = 0; i < reserved.length; i++) {
            byteBuffer.put(reserved[i]);
        }
        byteBuffer.putDouble(latitudeRad);
        byteBuffer.putDouble(longtitudeRad);
        byteBuffer.putFloat(altitudeM);
        byteBuffer.putFloat(dopplerVelocity);
        byteBuffer.putFloat(radarCrossSection);
        for (int i = 0; i < reserved2.length; i++) {
            byteBuffer.put(reserved2[i]);
        }
        byteBuffer.putShort(statusFlags);
        for (int i = 0; i < reserved3.length; i++) {
            byteBuffer.put(reserved3[i]);
        }
        byteBuffer.putInt(associatedPlots);
        byteBuffer.putFloat(timeSinceLastAssociation);
        byteBuffer.putFloat(age);
        byteBuffer.putFloat(posX);
        byteBuffer.putFloat(posY);
        byteBuffer.putFloat(posZ);
        byteBuffer.putFloat(velocityX);
        byteBuffer.putFloat(velocityY);
        byteBuffer.putFloat(velocityZ);
        byteBuffer.putFloat(posErrorX);
        byteBuffer.putFloat(posErrorY);
        byteBuffer.putFloat(posErrorZ);
        byteBuffer.putFloat(velocityErrorX);
        byteBuffer.putFloat(velocityErrorY);
        byteBuffer.putFloat(velocityErrorZ);
        byteBuffer.putFloat(testParam0);
        byteBuffer.putFloat(testParam1);
        byteBuffer.putFloat(testParam2);
        byteBuffer.putFloat(testParam3);
        byteBuffer.putFloat(testParam4);
        byteBuffer.putFloat(testParam5);
        byteBuffer.putFloat(testParam6);
        byteBuffer.putFloat(testParam7);
        byteBuffer.putFloat(testParam8);
        byteBuffer.putFloat(testParam9);
    }

    @Override
    public void fromByteArray(ByteBuffer byteBuffer) {
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        id = byteBuffer.getInt();
        type = byteBuffer.getInt();
        for (int i = 0; i < reserved.length; i++) {
            reserved[i] = byteBuffer.get();
        }
        latitudeRad = byteBuffer.getDouble();
        longtitudeRad = byteBuffer.getDouble();
        altitudeM = byteBuffer.getFloat();
        dopplerVelocity = byteBuffer.getFloat();
        radarCrossSection = byteBuffer.getFloat();
        for (int i = 0; i < reserved2.length; i++) {
            reserved2[i] = byteBuffer.get();
        }
        statusFlags = byteBuffer.getShort();
        for (int i = 0; i < reserved3.length; i++) {
            reserved3[i] = byteBuffer.get();
        }
        associatedPlots = byteBuffer.getInt();
        timeSinceLastAssociation = byteBuffer.getFloat();
        age = byteBuffer.getFloat();
        posX = byteBuffer.getFloat();
        posY = byteBuffer.getFloat();
        posZ = byteBuffer.getFloat();
        velocityX = byteBuffer.getFloat();
        velocityY = byteBuffer.getFloat();
        velocityZ = byteBuffer.getFloat();
        posErrorX = byteBuffer.getFloat();
        posErrorY = byteBuffer.getFloat();
        posErrorZ = byteBuffer.getFloat();
        velocityErrorX = byteBuffer.getFloat();
        velocityErrorY = byteBuffer.getFloat();
        velocityErrorZ = byteBuffer.getFloat();
        testParam0 = byteBuffer.getFloat();
        testParam1 = byteBuffer.getFloat();
        testParam2 = byteBuffer.getFloat();
        testParam3 = byteBuffer.getFloat();
        testParam4 = byteBuffer.getFloat();
        testParam5 = byteBuffer.getFloat();
        testParam6 = byteBuffer.getFloat();
        testParam7 = byteBuffer.getFloat();
        testParam8 = byteBuffer.getFloat();
        testParam9 = byteBuffer.getFloat();

    }
}

