package com.example.tester.tcp;

import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemas.candy.CandyMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class TcpListenerTest {

    private TcpListener listener;

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.close();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
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
    void start_pushesConfiguredPayloadImmediatelyOnAccept() throws Exception {
        int port = freePort();
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        listener = new TcpListener(port, payload);
        listener.start();

        try (Socket client = new Socket("127.0.0.1", port)) {
            client.setSoTimeout(3000);
            byte[] buffer = new byte[1024];
            int read = client.getInputStream().read(buffer);
            assertThat(new String(buffer, 0, read, StandardCharsets.UTF_8)).isEqualTo("hello");
        }
    }

    @Test
    void handleConnection_decodesAndLogsKnownReply() throws Exception {
        int port = freePort();
        byte[] payload = "ping".getBytes(StandardCharsets.UTF_8);
        listener = new TcpListener(port, payload);
        listener.start();

        try (StdoutCapture capture = new StdoutCapture();
             Socket client = new Socket("127.0.0.1", port)) {
            client.setSoTimeout(3000);
            client.getInputStream().read(new byte[1024]);

            CandyMessage candy = new CandyMessage("chocolate-bar", 250.5);
            byte[] candyBody = ReflectiveStructCodec.encode(candy);
            byte[] candyWireBytes = ProtocolHeaderCodec.encodeMessage(4001, System.currentTimeMillis(), candyBody);
            client.getOutputStream().write(candyWireBytes);
            client.getOutputStream().flush();

            awaitCondition(() -> capture.captured().contains("Decoded as CandyMessage"), Duration.ofSeconds(3));
            assertThat(capture.captured()).contains("chocolate-bar");
        }
    }

    @Test
    void close_stopsAcceptingNewConnections() throws Exception {
        int port = freePort();
        listener = new TcpListener(port, "x".getBytes(StandardCharsets.UTF_8));
        listener.start();

        listener.close();

        assertThatConnectFails(port);
    }

    private static void assertThatConnectFails(int port) {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            throw new AssertionError("Expected connect to fail after listener was closed");
        } catch (java.io.IOException expected) {
            // expected: nothing listening anymore
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
