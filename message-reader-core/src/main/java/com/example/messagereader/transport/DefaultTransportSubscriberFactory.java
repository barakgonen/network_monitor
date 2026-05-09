package com.example.messagereader.transport;

import com.example.messagereader.api.TransportProtocol;
import com.example.messagereader.transport.tcp.TcpTransportSubscriber;
import com.example.messagereader.transport.udp.UdpTransportSubscriber;

public class DefaultTransportSubscriberFactory implements TransportSubscriberFactory {
    @Override
    public TransportSubscriber createSubscriber(
            TransportProtocol protocol,
            int localPort,
            int bufferSizeBytes,
            RawPacketHandler packetHandler
    ) {
        return switch (protocol) {
            case UDP -> new UdpTransportSubscriber(localPort, bufferSizeBytes, packetHandler);
            case TCP -> new TcpTransportSubscriber(localPort, bufferSizeBytes, packetHandler);
        };
    }
}
