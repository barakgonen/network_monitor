package com.example.tester.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpPublisher {
    public void send(String host, int port, byte[] payload) {
        try (DatagramSocket socket = new DatagramSocket()) {
            send(socket, host, port, payload);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send UDP packet to " + host + ":" + port, e);
        }
    }

    /**
     * Sends from a caller-supplied, already-bound socket instead of a throwaway ephemeral one -
     * needed so a reply routed back to this socket's local port (e.g. by traffic-proxy-app's UDP
     * relay, which replies to whichever address+port the original request came from) actually
     * reaches something still listening, such as an active {@link UdpListener}.
     */
    public void send(DatagramSocket socket, String host, int port, byte[] payload) {
        try {
            InetAddress address = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send UDP packet to " + host + ":" + port, e);
        }
    }
}
