package com.example.binaryserdes.protocol.pojos;

public class MultipleComplexMessage {
    private int opcode;
    private MyPosition first;
    private int one;
    private int two;
    private double three;
    private MyPosition four;

    public MultipleComplexMessage() {
    }

    public MultipleComplexMessage(int opcode, MyPosition first, int one, int two, double three, MyPosition four) {
        this.opcode = opcode;
        this.first = first;
        this.one = one;
        this.two = two;
        this.three = three;
        this.four = four;
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public MyPosition getFirst() {
        return first;
    }

    public void setFirst(MyPosition first) {
        this.first = first;
    }

    public int getOne() {
        return one;
    }

    public void setOne(int one) {
        this.one = one;
    }

    public int getTwo() {
        return two;
    }

    public void setTwo(int two) {
        this.two = two;
    }

    public double getThree() {
        return three;
    }

    public void setThree(double three) {
        this.three = three;
    }

    public MyPosition getFour() {
        return four;
    }

    public void setFour(MyPosition four) {
        this.four = four;
    }
}
