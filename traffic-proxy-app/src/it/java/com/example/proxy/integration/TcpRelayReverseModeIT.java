package com.example.proxy.integration;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import com.example.destination.listener.TcpEchoListener;
import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.example.proxy.relay.TcpRelay;
import com.example.binaryserdes.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.tester.schemas.candy.CandyMessage;
import com.example.tester.tcp.TcpListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full case-1 role reversal, end to end, using the actual production classes from all three
 * apps: a real {@link TcpListener} (traffic-tester-app, acting as the TCP server that pushes a
 * Candy message on accept), a real {@link TcpRelay} in reverse mode (traffic-proxy-app, dialing
 * in to the tester and accepting the destination side), and a real {@link TcpEchoListener} in
 * CLIENT mode (traffic-destination-app, dialing out to the relay) - proving the three real
 * components interoperate, not just each side's own unit tests against hand-rolled doubles.
 */
class TcpRelayReverseModeIT {

    private TcpListener testerListener;
    private TcpRelay relay;
    private TcpEchoListener destinationListener;

    @AfterEach
    void tearDown() {
        if (testerListener != null) {
            testerListener.close();
        }
        if (relay != null) {
            relay.stop();
        }
        if (destinationListener != null) {
            destinationListener.stop();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static EndpointConfig endpoint(String host, int port) {
        EndpointConfig endpoint = new EndpointConfig();
        endpoint.setHost(host);
        endpoint.setPort(port);
        return endpoint;
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
    void testerServer_relayReverseMode_destinationClient_fullRoundTripAndMirror() throws Exception {
        int testerPort = freePort();
        int relayDestinationPort = freePort();

        CapturingMirror mirror = new CapturingMirror();

        // 1. traffic-tester-app: real TCP server, pushes a real Candy message on accept.
        CandyMessage candy = new CandyMessage("gummy-bear", 180.0);
        byte[] candyBody = ReflectiveStructCodec.encode(candy);
        byte[] candyWireBytes = ProtocolHeaderCodec.encodeMessage(4001, System.currentTimeMillis(), candyBody);
        testerListener = new TcpListener(testerPort, candyWireBytes);
        testerListener.start();

        // 2. traffic-destination-app: real TCP client, echoes bytes back.
        InterfaceEntry destinationConfig = new InterfaceEntry();
        destinationConfig.setKey("candy-reverse");
        destinationConfig.setProtocol("TCP");
        destinationConfig.setPort(relayDestinationPort);
        destinationConfig.setMode("CLIENT");
        destinationConfig.setHost("127.0.0.1");
        destinationConfig.setReplyMode(ReplyMode.ECHO);
        destinationListener = new TcpEchoListener(destinationConfig);
        destinationListener.start();

        // 3. traffic-proxy-app: real reverse-mode relay tying the two together.
        RelayEntry relayEntry = new RelayEntry();
        relayEntry.setKey("candy-reverse");
        relayEntry.setProtocol("TCP");
        relayEntry.setListen(endpoint("127.0.0.1", testerPort));
        relayEntry.setDestination(endpoint("0.0.0.0", relayDestinationPort));
        relayEntry.setMirror(endpoint("127.0.0.1", mirror.port()));
        relayEntry.setListenMode("CLIENT");
        relayEntry.setDestinationMode("SERVER");
        relay = new TcpRelay(relayEntry);
        relay.start();

        try {
            // The tester pushed its Candy message the moment the relay connected in; it should
            // now flow all the way through to destination-app and echo all the way back.
            awaitCondition(() -> mirror.receivedBytes().length >= candyWireBytes.length * 2, Duration.ofSeconds(5));

            byte[] mirrored = mirror.receivedBytes();
            byte[] expectedRequestThenReply = concat(candyWireBytes, candyWireBytes);
            assertThat(mirrored).isEqualTo(expectedRequestThenReply);
        } finally {
            mirror.close();
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static final class CapturingMirror implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ByteArrayOutputStream received = new ByteArrayOutputStream();
        private final Object lock = new Object();
        private volatile boolean running = true;

        CapturingMirror() throws Exception {
            serverSocket = new ServerSocket(0);
            Thread thread = new Thread(this::acceptLoop, "test-mirror-accept");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        byte[] receivedBytes() {
            synchronized (lock) {
                return received.toByteArray();
            }
        }

        private void acceptLoop() {
            while (running) {
                Socket connection;
                try {
                    connection = serverSocket.accept();
                } catch (Exception e) {
                    return;
                }
                Thread reader = new Thread(() -> readLoop(connection), "test-mirror-read");
                reader.setDaemon(true);
                reader.start();
            }
        }

        private void readLoop(Socket connection) {
            try (connection) {
                InputStream in = connection.getInputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    synchronized (lock) {
                        received.write(buffer, 0, read);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        public void close() throws Exception {
            running = false;
            serverSocket.close();
        }
    }
}
