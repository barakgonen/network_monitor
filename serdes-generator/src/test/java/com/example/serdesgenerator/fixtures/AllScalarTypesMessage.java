package com.example.serdesgenerator.fixtures;

import java.nio.ByteBuffer;

/** Fixed-layout root fixture exercising every supported scalar Java type. */
public class AllScalarTypesMessage {
    private byte byteField;
    private short shortField;
    private char charField;
    private int intField;
    private long longField;
    private float floatField;
    private double doubleField;

    public AllScalarTypesMessage(byte[] payload) {
        // stub - only the method signature matters for generation, never invoked by these tests
    }

    public void toByteArray(ByteBuffer buffer) {
        // stub
    }
}
