package com.example.binaryserdes.protocol.pojos;

public class ComplexPosition {
    private MyPosition position;
    private int bg;

    public ComplexPosition() {
    }

    public ComplexPosition(MyPosition position, int bg) {
        this.position = position;
        this.bg = bg;
    }

    public MyPosition getPosition() {
        return position;
    }

    public void setPosition(MyPosition position) {
        this.position = position;
    }

    public int getBg() {
        return bg;
    }

    public void setBg(int bg) {
        this.bg = bg;
    }
}
