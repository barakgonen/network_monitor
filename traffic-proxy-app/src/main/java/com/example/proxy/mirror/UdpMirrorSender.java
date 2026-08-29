package com.example.proxy.mirror;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Fire-and-forget UDP duplicate sender. A mirror failure must never affect the real relay path,
 * so every exception is swallowed and logged rather than thrown.
 */
public class UdpMirrorSender {

    public void send(String host, int port, byte[] payload, String relayKey) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("[" + relayKey + "] mirror UDP send to " + host + ":" + port + " failed: " + e.getMessage());
        }
    }
}
