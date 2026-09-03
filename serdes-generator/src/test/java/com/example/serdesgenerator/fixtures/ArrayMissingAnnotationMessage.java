package com.example.serdesgenerator.fixtures;

import java.nio.ByteBuffer;

public class ArrayMissingAnnotationMessage {
    private byte[] values = new byte[5];

    public ArrayMissingAnnotationMessage(byte[] payload) {
    }

    public void toByteArray(ByteBuffer buffer) {
    }
}
