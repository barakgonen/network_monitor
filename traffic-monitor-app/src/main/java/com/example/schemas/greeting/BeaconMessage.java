package com.example.schemas.greeting;

import java.nio.ByteBuffer;

public record BeaconMessage(double lat, double lon) {
    public static BeaconMessage fromByteBuffer(ByteBuffer buffer) {
        return new BeaconMessage(buffer.getDouble(), buffer.getDouble());
    }

    public void toByteArray(ByteBuffer buffer) {
        buffer.putDouble(lat);
        buffer.putDouble(lon);
    }
}
