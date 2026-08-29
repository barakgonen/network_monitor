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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void distinctProducersGetIndependentNatEntriesAndCorrectReplyRouting() throws Exception {
        try (DatagramSocket producerA = new DatagramSocket();
             DatagramSocket producerB = new DatagramSocket();
             DatagramSocket destination = new DatagramSocket(0);
             DatagramSocket mirror = new DatagramSocket(0)) {

            int listenPort = freePort();
            UdpRelay relay = new UdpRelay(relayEntry(listenPort, destination.getLocalPort(), mirror.getLocalPort()));
            relay.start();

            try {
                destination.setSoTimeout(3000);

                byte[] payloadA = "from-a".getBytes();
                producerA.send(new DatagramPacket(payloadA, payloadA.length, InetAddress.getLoopbackAddress(), listenPort));
                DatagramPacket receivedFromA = new DatagramPacket(new byte[1024], 1024);
                destination.receive(receivedFromA);
                assertThat(new String(receivedFromA.getData(), 0, receivedFromA.getLength())).isEqualTo("from-a");

                byte[] payloadB = "from-b".getBytes();
                producerB.send(new DatagramPacket(payloadB, payloadB.length, InetAddress.getLoopbackAddress(), listenPort));
                DatagramPacket receivedFromB = new DatagramPacket(new byte[1024], 1024);
                destination.receive(receivedFromB);
                assertThat(new String(receivedFromB.getData(), 0, receivedFromB.getLength())).isEqualTo("from-b");

                // Destination sees A and B arrive via different ephemeral relay-outbound ports -
                // proof each producer got its own independent NAT entry, not a shared one.
                assertThat(receivedFromA.getPort()).isNotEqualTo(receivedFromB.getPort());
                awaitCondition(() -> relay.natTableSize() == 2, Duration.ofSeconds(2));

                // Reply to B only; it must route back to producerB, never to producerA.
                byte[] replyToB = "reply-b".getBytes();
                destination.send(new DatagramPacket(replyToB, replyToB.length, receivedFromB.getAddress(), receivedFromB.getPort()));

                assertThat(new String(receiveWithTimeout(producerB, 3000))).isEqualTo("reply-b");

                producerA.setSoTimeout(300);
                assertThatThrownBy(() -> producerA.receive(new DatagramPacket(new byte[1024], 1024)))
                        .isInstanceOf(java.net.SocketTimeoutException.class);
            } finally {
                relay.stop();
            }
        }
    }

    @Test
    void fixedReplyPortMode_relaysBidirectionally_andMirrorsBoth() throws Exception {
        try (DatagramSocket producer = new DatagramSocket();
             DatagramSocket destination = new DatagramSocket(0);
             DatagramSocket mirror = new DatagramSocket(0)) {

            int listenPort = freePort();
            int replyPort = freePort();
            RelayEntry entry = relayEntry(listenPort, destination.getLocalPort(), mirror.getLocalPort());
            entry.setReplyPort(replyPort);
            UdpRelay relay = new UdpRelay(entry);
            relay.start();

            try {
                byte[] requestPayload = "hello".getBytes();
                producer.send(new DatagramPacket(requestPayload, requestPayload.length,
                        InetAddress.getLoopbackAddress(), listenPort));

                destination.setSoTimeout(3000);
                DatagramPacket receivedAtDestination = new DatagramPacket(new byte[1024], 1024);
                destination.receive(receivedAtDestination);
                assertThat(new String(receivedAtDestination.getData(), 0, receivedAtDestination.getLength()))
                        .isEqualTo("hello");
                // The relay's outbound socket is bound to the configured fixed port, not an
                // OS-assigned ephemeral one.
                assertThat(receivedAtDestination.getPort()).isEqualTo(replyPort);

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
    void fixedReplyPortMode_routesReplyToMostRecentProducer_notAnEarlierOne() throws Exception {
        try (DatagramSocket producerA = new DatagramSocket();
             DatagramSocket producerB = new DatagramSocket();
             DatagramSocket destination = new DatagramSocket(0);
             DatagramSocket mirror = new DatagramSocket(0)) {

            int listenPort = freePort();
            int replyPort = freePort();
            RelayEntry entry = relayEntry(listenPort, destination.getLocalPort(), mirror.getLocalPort());
            entry.setReplyPort(replyPort);
            UdpRelay relay = new UdpRelay(entry);
            relay.start();

            try {
                destination.setSoTimeout(3000);

                byte[] payloadA = "from-a".getBytes();
                producerA.send(new DatagramPacket(payloadA, payloadA.length, InetAddress.getLoopbackAddress(), listenPort));
                destination.receive(new DatagramPacket(new byte[1024], 1024));

                byte[] payloadB = "from-b".getBytes();
                producerB.send(new DatagramPacket(payloadB, payloadB.length, InetAddress.getLoopbackAddress(), listenPort));
                DatagramPacket receivedFromB = new DatagramPacket(new byte[1024], 1024);
                destination.receive(receivedFromB);

                // Both requests arrived from the same fixed port - this mode can't tell producers
                // apart, so the single reply must go to whoever sent most recently (B), per the
                // agreed "last producer wins" trade-off.
                byte[] reply = "reply".getBytes();
                destination.send(new DatagramPacket(reply, reply.length, receivedFromB.getAddress(), receivedFromB.getPort()));

                assertThat(new String(receiveWithTimeout(producerB, 3000))).isEqualTo("reply");

                producerA.setSoTimeout(300);
                assertThatThrownBy(() -> producerA.receive(new DatagramPacket(new byte[1024], 1024)))
                        .isInstanceOf(java.net.SocketTimeoutException.class);
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
