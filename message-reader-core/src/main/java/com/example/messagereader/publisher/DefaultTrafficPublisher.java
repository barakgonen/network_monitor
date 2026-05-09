package com.example.messagereader.publisher;

import com.example.messagereader.api.PublishTarget;
import com.example.messagereader.api.TrafficPublisher;
import com.example.messagereader.api.TransportProtocol;
import com.example.messagereader.publisher.tcp.TcpTrafficPublisher;
import com.example.messagereader.publisher.udp.UdpTrafficPublisher;

public class DefaultTrafficPublisher implements TrafficPublisher {
    private final UdpTrafficPublisher udpTrafficPublisher = new UdpTrafficPublisher();
    private final TcpTrafficPublisher tcpTrafficPublisher = new TcpTrafficPublisher();

    @Override
    public void publish(PublishTarget target, byte[] payload) {
        if (target.protocol() == TransportProtocol.UDP) {
            udpTrafficPublisher.publish(target, payload);
            return;
        }

        tcpTrafficPublisher.publish(target, payload);
    }
}
