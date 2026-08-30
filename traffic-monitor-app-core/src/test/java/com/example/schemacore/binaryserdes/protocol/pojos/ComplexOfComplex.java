package com.example.schemacore.binaryserdes.protocol.pojos;

public class ComplexOfComplex {
    private int opcode;
    private ComplexPosition first;
    private int one;

    public ComplexOfComplex() {
    }

    public ComplexOfComplex(int opcode, ComplexPosition first, int one) {
        this.opcode = opcode;
        this.first = first;
        this.one = one;
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public ComplexPosition getFirst() {
        return first;
    }

    public void setFirst(ComplexPosition first) {
        this.first = first;
    }

    public int getOne() {
        return one;
    }

    public void setOne(int one) {
        this.one = one;
    }
}
