package com.example.schemacore.binaryserdes.protocol.pojos;

public class MyPositionMessage {
    private int opcode;
    private MyPosition myPosition;

    public MyPositionMessage() {
    }

    public MyPositionMessage(int opcode, MyPosition myPosition) {
        this.opcode = opcode;
        this.myPosition = myPosition;
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public MyPosition getMyPosition() {
        return myPosition;
    }

    public void setMyPosition(MyPosition myPosition) {
        this.myPosition = myPosition;
    }
}
