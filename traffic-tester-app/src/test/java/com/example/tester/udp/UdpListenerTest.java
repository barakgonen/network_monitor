package com.example.tester.udp;

import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.tester.schemas.ping.PingMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class UdpListenerTest {

    private UdpListener listener;

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.close();
        }
    }

    private static int freePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
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
    void start_bindsSocketSynchronously() throws Exception {
        int port = freePort();
        listener = new UdpListener(port, 65507);

        listener.start();

        assertThat(listener.socket()).isNotNull();
        assertThat(listener.socket().isClosed()).isFalse();
        assertThat(listener.socket().getLocalPort()).isEqualTo(port);
    }

    @Test
    void close_stopsListenerAndClosesSocket() throws Exception {
        int port = freePort();
        listener = new UdpListener(port, 65507);
        listener.start();

        listener.close();

        assertThat(listener.socket().isClosed()).isTrue();
    }

    @Test
    void decodesKnownMessageAndLogsIt() throws Exception {
        int port = freePort();
        listener = new UdpListener(port, 65507);
        listener.start();

        try (StdoutCapture capture = new StdoutCapture()) {
            byte[] pingBody = ReflectiveStructCodec.encode(new PingMessage(9));
            byte[] pingWireBytes = ProtocolHeaderCodec.encodeMessage(3001, System.currentTimeMillis(), pingBody);

            try (DatagramSocket sender = new DatagramSocket()) {
                sender.send(new DatagramPacket(pingWireBytes, pingWireBytes.length, InetAddress.getLoopbackAddress(), port));
            }

            awaitCondition(() -> capture.captured().contains("Decoded as PingMessage"), Duration.ofSeconds(2));
            assertThat(capture.captured()).contains("sequence=9");
        }
    }

    @Test
    void sharedSocketRoundTrip_replySentBackToListenerPortArrivesAtListener() throws Exception {
        int listenerPort = freePort();
        listener = new UdpListener(listenerPort, 65507);
        listener.start();

        try (StdoutCapture capture = new StdoutCapture();
             DatagramSocket peer = new DatagramSocket(0)) {
            peer.setSoTimeout(2000);

            UdpPublisher publisher = new UdpPublisher();
            byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
            publisher.send(listener.socket(), "127.0.0.1", peer.getLocalPort(), payload);

            byte[] buffer = new byte[1024];
            DatagramPacket received = new DatagramPacket(buffer, buffer.length);
            peer.receive(received);

            // Confirms the send really came from the listener's own bound port, not an ephemeral one.
            assertThat(received.getPort()).isEqualTo(listenerPort);

            byte[] reply = "world".getBytes(StandardCharsets.UTF_8);
            peer.send(new DatagramPacket(reply, reply.length, received.getAddress(), received.getPort()));

            awaitCondition(() -> capture.captured().contains("Text: world"), Duration.ofSeconds(2));
        }
    }

    private static final class StdoutCapture implements AutoCloseable {
        private final PrintStream original = System.out;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StdoutCapture() {
            System.setOut(new PrintStream(buffer));
        }

        String captured() {
            return buffer.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            System.setOut(original);
        }
    }
}
