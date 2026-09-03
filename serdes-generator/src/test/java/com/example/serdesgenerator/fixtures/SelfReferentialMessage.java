package com.example.serdesgenerator.fixtures;

import java.nio.ByteBuffer;

public class SelfReferentialMessage {
    private SelfReferentialMessage child;

    public SelfReferentialMessage(byte[] payload) {
    }

    public void toByteArray(ByteBuffer buffer) {
    }
}
