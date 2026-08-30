package com.example.schemacore.binaryserdes.protocol.pojos;

public class Position {
    private int opcode;
    private double lat;
    private double lon;
    private double alt;

    public Position() {
    }

    public Position(int opcode, double lat, double lon, double alt) {
        this.opcode = opcode;
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public double getAlt() {
        return alt;
    }

    public void setAlt(double alt) {
        this.alt = alt;
    }
}
