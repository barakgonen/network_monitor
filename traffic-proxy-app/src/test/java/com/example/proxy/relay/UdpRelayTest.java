package com.example.proxy.relay;

import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class UdpRelayTest {

    private static int freePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
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

    private static EndpointConfig endpoint(String host, int port) {
        EndpointConfig endpoint = new EndpointConfig();
        endpoint.setHost(host);
        endpoint.setPort(port);
        return endpoint;
    }

    private static byte[] receiveWithTimeout(DatagramSocket socket, long timeoutMs) throws Exception {
        socket.setSoTimeout((int) timeoutMs);
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        byte[] result = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), result, 0, packet.getLength());
        return result;
    }

    private static void awaitCondition(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @Test
    void relaysRequestAndReplyBidirectionally_andMirrorsBoth() throws Exception {
        try (DatagramSocket producer = new DatagramSocket();
             DatagramSocket destination = new DatagramSocket(0);
             DatagramSocket mirror = new DatagramSocket(0)) {

            int listenPort = freePort();
            UdpRelay relay = new UdpRelay(relayEntry(listenPort, destination.getLocalPort(), mirror.getLocalPort()));
            relay.start();

            try {
                byte[] requestPayload = "hello".getBytes();
                producer.send(new DatagramPacket(requestPayload, requestPayload.length,
                        InetAddress.getLoopbackAddress(), listenPort));

                destination.setSoTimeout(3000);
                byte[] buffer = new byte[1024];
                DatagramPacket receivedAtDestination = new DatagramPacket(buffer, buffer.length);
                destination.receive(receivedAtDestination);
                assertThat(new String(receivedAtDestination.getData(), 0, receivedAtDestination.getLength()))
                        .isEqualTo("hello");

                assertThat(new String(receiveWithTimeout(mirror, 3000))).isEqualTo("hello");

                byte[] replyPayload = "world".getBytes();
                destination.send(new DatagramPacket(replyPayload, replyPayload.length,
                        receivedAtDestination.getAddress(), receivedAtDestination.getPort()));

                assertThat(new String(receiveWithTimeout(producer, 3000))).isEqualTo("world");
                assertThat(new String(receiveWithTimeout(mirror, 3000))).isEqualTo("world");
            } finally {
                relay.stop();
            }
        }
    }

    @Test
    void idleNatEntriesAreSweptAfterTimeout() throws Exception {
        try (DatagramSocket producer = new DatagramSocket();
             DatagramSocket destination = new DatagramSocket(0);
             DatagramSocket mirror = new DatagramSocket(0)) {

            int listenPort = freePort();
            RelayEntry entry = relayEntry(listenPort, destination.getLocalPort(), mirror.getLocalPort());
            entry.setNatIdleTimeoutMillis(200);
            entry.setNatIdleSweepIntervalMillis(100);
            UdpRelay relay = new UdpRelay(entry);
            relay.start();

            try {
                byte[] payload = "hello".getBytes();
                producer.send(new DatagramPacket(payload, payload.length, InetAddress.getLoopbackAddress(), listenPort));

                destination.setSoTimeout(3000);
                destination.receive(new DatagramPacket(new byte[1024], 1024));

                awaitCondition(() -> relay.natTableSize() == 1, Duration.ofSeconds(1));
                awaitCondition(() -> relay.natTableSize() == 0, Duration.ofSeconds(2));
            } finally {
                relay.stop();
            }
        }
    }
}
