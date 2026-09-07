package com.example.serdesgenerator.fixtures;

import java.nio.ByteBuffer;

/**
 * Like {@link ArrayOfScalarsMessage} but deliberately has no {@code @FixedArrayLength} - its
 * no-arg constructor initializes {@code values} to its wire-mandated length instead, mirroring
 * {@code RadaTracksExtended}'s shape. Proves {@code ProtocolJsonGenerator} can infer the array
 * length from a default instance when the annotation is absent.
 */
public class ArrayLengthInferredMessage {
    private byte[] values = new byte[5];

    public ArrayLengthInferredMessage() {
    }

    public ArrayLengthInferredMessage(byte[] payload) {
    }

    public void toByteArray(ByteBuffer buffer) {
    }
}
