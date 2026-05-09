package com.example.messagereader.transport;

import com.example.messagereader.api.TransportProtocol;

public interface TransportSubscriberFactory {
    TransportSubscriber createSubscriber(
            TransportProtocol protocol,
            int localPort,
            int bufferSizeBytes,
            RawPacketHandler packetHandler
    );
}
