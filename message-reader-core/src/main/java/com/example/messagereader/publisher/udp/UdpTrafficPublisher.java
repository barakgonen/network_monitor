package com.example.messagereader.publisher.udp;

import com.example.messagereader.api.MessageReaderException;
import com.example.messagereader.api.PublishTarget;
import com.example.messagereader.api.TrafficPublisher;
import com.example.messagereader.api.TransportProtocol;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpTrafficPublisher implements TrafficPublisher {
    @Override
    public void publish(PublishTarget target, byte[] payload) {
        if (target.protocol() != TransportProtocol.UDP) {
            throw new IllegalArgumentException("UdpTrafficPublisher supports only UDP targets");
        }

        byte[] safePayload = payload == null ? new byte[0] : payload;

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(target.host());
            DatagramPacket packet = new DatagramPacket(safePayload, safePayload.length, address, target.port());
            socket.send(packet);
        } catch (Exception e) {
            throw new MessageReaderException("Failed sending UDP packet to " + target.host() + ":" + target.port(), e);
        }
    }
}
