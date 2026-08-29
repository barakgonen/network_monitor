package com.example.proxy.integration;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import com.example.destination.listener.UdpEchoListener;
import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.example.proxy.relay.UdpRelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real {@link UdpRelay} wired against a real {@link UdpEchoListener} in ECHO/NONE mode - the
 * generic (protocol-unaware) raw-byte relay path used by fruit/weather-style interfaces, as
 * opposed to {@link UdpRelayPingPongIT}'s Ping/Pong-specific path.
 */
class UdpRelayEchoIT {

    private UdpRelay relay;
    private UdpEchoListener destinationListener;

    @AfterEach
    void tearDown() {
        if (relay != null) {
            relay.stop();
        }
        if (destinationListener != null) {
            destinationListener.stop();
        }
    }

    private static int freePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static EndpointConfig endpoint(String host, int port) {
        EndpointConfig endpoint = new EndpointConfig();
        endpoint.setHost(host);
        endpoint.setPort(port);
        return endpoint;
    }

    private static InterfaceEntry destinationConfig(int port, ReplyMode replyMode) {
        InterfaceEntry entry = new InterfaceEntry();
        entry.setKey("fruit");
        entry.setProtocol("UDP");
        entry.setPort(port);
        entry.setReplyMode(replyMode);
        return entry;
    }

    private static RelayEntry relayEntry(int listenPort, int destinationPort, int mirrorPort) {
        RelayEntry entry = new RelayEntry();
        entry.setKey("fruit");
        entry.setProtocol("UDP");
        entry.setListen(endpoint("0.0.0.0", listenPort));
        entry.setDestination(endpoint("127.0.0.1", destinationPort));
        entry.setMirror(endpoint("127.0.0.1", mirrorPort));
        return entry;
    }

    private static byte[] receive(DatagramSocket socket) throws Exception {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        byte[] result = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), result, 0, packet.getLength());
        return result;
    }

    @Test
    void echoMode_relaysAndMirrorsBothDirections() throws Exception {
        int destinationPort = freePort();
        destinationListener = new UdpEchoListener(destinationConfig(destinationPort, ReplyMode.ECHO));
        destinationListener.start();

        try (DatagramSocket producer = new DatagramSocket();
             DatagramSocket mirror = new DatagramSocket(0)) {
            int listenPort = freePort();
            relay = new UdpRelay(relayEntry(listenPort, destinationPort, mirror.getLocalPort()));
            relay.start();

            byte[] payload = "orange".getBytes();
            producer.send(new DatagramPacket(payload, payload.length, InetAddress.getLoopbackAddress(), listenPort));

            producer.setSoTimeout(3000);
            assertThat(new String(receive(producer))).isEqualTo("orange");

            mirror.setSoTimeout(3000);
            assertThat(new String(receive(mirror))).isEqualTo("orange");
            assertThat(new String(receive(mirror))).isEqualTo("orange");
        }
    }

    @Test
    void noneMode_relaysOneWayTraffic_mirroredButNoReply() throws Exception {
        int destinationPort = freePort();
        destinationListener = new UdpEchoListener(destinationConfig(destinationPort, ReplyMode.NONE));
        destinationListener.start();

        try (DatagramSocket producer = new DatagramSocket();
             DatagramSocket mirror = new DatagramSocket(0)) {
            int listenPort = freePort();
            relay = new UdpRelay(relayEntry(listenPort, destinationPort, mirror.getLocalPort()));
            relay.start();

            byte[] payload = "weather-reading".getBytes();
            producer.send(new DatagramPacket(payload, payload.length, InetAddress.getLoopbackAddress(), listenPort));

            mirror.setSoTimeout(3000);
            assertThat(new String(receive(mirror))).isEqualTo("weather-reading");

            producer.setSoTimeout(300);
            assertThatThrownBy(() -> producer.receive(new DatagramPacket(new byte[1024], 1024)))
                    .isInstanceOf(java.net.SocketTimeoutException.class);
        }
    }
}
