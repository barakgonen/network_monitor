package com.example.messagereader.api;

import java.time.Instant;
import java.util.Arrays;

public final class RawTrafficPacket {
    private final TransportProtocol protocol;
    private final int localPort;
    private final String remoteAddress;
    private final byte[] payload;
    private final Instant receivedAt;

    public RawTrafficPacket(
            TransportProtocol protocol,
            int localPort,
            String remoteAddress,
            byte[] payload,
            Instant receivedAt
    ) {
        this.protocol = protocol;
        this.localPort = localPort;
        this.remoteAddress = remoteAddress;
        this.payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        this.receivedAt = receivedAt;
    }

    public TransportProtocol protocol() {
        return protocol;
    }

    public int localPort() {
        return localPort;
    }

    public String remoteAddress() {
        return remoteAddress;
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public int payloadSizeBytes() {
        return payload.length;
    }

    public Instant receivedAt() {
        return receivedAt;
    }
}
