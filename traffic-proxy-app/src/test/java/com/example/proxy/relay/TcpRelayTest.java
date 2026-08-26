package com.example.proxy.relay;

import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class TcpRelayTest {

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static RelayEntry relayEntry(int listenPort, int destinationPort, int mirrorPort) {
        RelayEntry entry = new RelayEntry();
        entry.setKey("candy");
        entry.setProtocol("TCP");
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

    private static int readFully(InputStream in, byte[] buffer, int expectedLength) throws IOException {
        int total = 0;
        while (total < expectedLength) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read == -1) {
                break;
            }
            total += read;
        }
        return total;
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
    void relaysRequestAndReplyBidirectionally_andMirrorsBothDirections() throws Exception {
        EchoServer destination = new EchoServer();
        CapturingServer mirror = new CapturingServer();
        int listenPort = freePort();
        TcpRelay relay = new TcpRelay(relayEntry(listenPort, destination.port(), mirror.port()));
        relay.start();

        try {
            try (Socket client = new Socket("127.0.0.1", listenPort)) {
                client.setSoTimeout(3000);
                OutputStream out = client.getOutputStream();
                InputStream in = client.getInputStream();

                byte[] payload = "hello".getBytes();
                out.write(payload);
                out.flush();

                byte[] buffer = new byte[1024];
                int read = readFully(in, buffer, payload.length);
                assertThat(new String(buffer, 0, read)).isEqualTo("hello");
            }

            awaitCondition(() -> mirror.receivedBytes().length >= "hellohello".length(), Duration.ofSeconds(3));
            assertThat(new String(mirror.receivedBytes())).isEqualTo("hellohello");
        } finally {
            relay.stop();
            destination.close();
            mirror.close();
        }
    }

    @Test
    void relayPreservesMultipleSequentialFramedMessagesOverOneConnection() throws Exception {
        EchoServer destination = new EchoServer();
        CapturingServer mirror = new CapturingServer();
        int listenPort = freePort();
        TcpRelay relay = new TcpRelay(relayEntry(listenPort, destination.port(), mirror.port()));
        relay.start();

        try {
            try (Socket client = new Socket("127.0.0.1", listenPort)) {
                client.setSoTimeout(3000);
                OutputStream out = client.getOutputStream();
                InputStream in = client.getInputStream();

                String[] messages = {"one", "two", "three"};
                for (String message : messages) {
                    byte[] payload = message.getBytes();
                    out.write(payload);
                    out.flush();

                    byte[] buffer = new byte[1024];
                    int read = readFully(in, buffer, payload.length);
                    assertThat(new String(buffer, 0, read)).isEqualTo(message);
                }
            }

            // Exactly the request+echoed-reply bytes of all three messages, in chronological
            // order, on the single persistent mirror connection - proves multiple discrete
            // messages over one client connection don't get scrambled or dropped.
            String expectedMirrorStream = "oneonetwotwothreethree";
            awaitCondition(() -> mirror.receivedBytes().length >= expectedMirrorStream.length(), Duration.ofSeconds(3));
            assertThat(new String(mirror.receivedBytes())).isEqualTo(expectedMirrorStream);
        } finally {
            relay.stop();
            destination.close();
            mirror.close();
        }
    }

    @Test
    void mirrorConnectionIsReusedAcrossMultipleClientConnections() throws Exception {
        EchoServer destination = new EchoServer();
        CapturingServer mirror = new CapturingServer();
        int listenPort = freePort();
        TcpRelay relay = new TcpRelay(relayEntry(listenPort, destination.port(), mirror.port()));
        relay.start();

        try {
            sendAndAwaitEcho(listenPort, "first");
            sendAndAwaitEcho(listenPort, "second");

            awaitCondition(() -> mirror.acceptedConnectionCount() == 1, Duration.ofSeconds(3));
            assertThat(mirror.acceptedConnectionCount()).isEqualTo(1);
        } finally {
            relay.stop();
            destination.close();
            mirror.close();
        }
    }

    private void sendAndAwaitEcho(int listenPort, String message) throws IOException {
        try (Socket client = new Socket("127.0.0.1", listenPort)) {
            client.setSoTimeout(3000);
            OutputStream out = client.getOutputStream();
            InputStream in = client.getInputStream();
            byte[] payload = message.getBytes();
            out.write(payload);
            out.flush();
            byte[] buffer = new byte[1024];
            readFully(in, buffer, payload.length);
        }
    }

    @Test
    void destinationUnreachable_closesClientConnectionPromptly() throws Exception {
        int unusedDestinationPort = freePort();
        CapturingServer mirror = new CapturingServer();
        int listenPort = freePort();
        RelayEntry entry = relayEntry(listenPort, unusedDestinationPort, mirror.port());
        entry.setDestinationConnectTimeoutMillis(500);
        TcpRelay relay = new TcpRelay(entry);
        relay.start();

        try {
            try (Socket client = new Socket("127.0.0.1", listenPort)) {
                client.setSoTimeout(3000);
                int result = client.getInputStream().read();
                assertThat(result).isEqualTo(-1);
            }
        } finally {
            relay.stop();
            mirror.close();
        }
    }

    @Test
    void reverseMode_relaysBidirectionally_andMirrorsBoth() throws Exception {
        try (ServerSocket producerServer = new ServerSocket(0)) {
            int producerServerPort = producerServer.getLocalPort();
            int destinationServerPort = freePort();
            CapturingServer mirror = new CapturingServer();

            RelayEntry entry = new RelayEntry();
            entry.setKey("candy-reverse");
            entry.setProtocol("TCP");
            entry.setListen(endpoint("127.0.0.1", producerServerPort));
            entry.setDestination(endpoint("0.0.0.0", destinationServerPort));
            entry.setMirror(endpoint("127.0.0.1", mirror.port()));
            entry.setListenMode("CLIENT");
            entry.setDestinationMode("SERVER");

            TcpRelay relay = new TcpRelay(entry);
            relay.start();

            try {
                producerServer.setSoTimeout(3000);
                Socket producerSide = producerServer.accept();
                producerSide.setSoTimeout(3000);

                Socket destinationSide = new Socket("127.0.0.1", destinationServerPort);
                destinationSide.setSoTimeout(3000);

                byte[] payload = "hello".getBytes();
                producerSide.getOutputStream().write(payload);
                producerSide.getOutputStream().flush();

                byte[] buffer = new byte[1024];
                int read = readFully(destinationSide.getInputStream(), buffer, payload.length);
                assertThat(new String(buffer, 0, read)).isEqualTo("hello");

                byte[] reply = "world".getBytes();
                destinationSide.getOutputStream().write(reply);
                destinationSide.getOutputStream().flush();

                byte[] replyBuffer = new byte[1024];
                int replyRead = readFully(producerSide.getInputStream(), replyBuffer, reply.length);
                assertThat(new String(replyBuffer, 0, replyRead)).isEqualTo("world");

                awaitCondition(() -> mirror.receivedBytes().length >= "helloworld".length(), Duration.ofSeconds(3));
                assertThat(new String(mirror.receivedBytes())).isEqualTo("helloworld");

                producerSide.close();
                destinationSide.close();
            } finally {
                relay.stop();
                mirror.close();
            }
        }
    }

    @Test
    void reverseMode_reconnectsToProducerAfterDisconnect() throws Exception {
        try (ServerSocket producerServer = new ServerSocket(0)) {
            int producerServerPort = producerServer.getLocalPort();
            int destinationServerPort = freePort();
            CapturingServer mirror = new CapturingServer();

            RelayEntry entry = new RelayEntry();
            entry.setKey("candy-reverse");
            entry.setProtocol("TCP");
            entry.setListen(endpoint("127.0.0.1", producerServerPort));
            entry.setDestination(endpoint("0.0.0.0", destinationServerPort));
            entry.setMirror(endpoint("127.0.0.1", mirror.port()));
            entry.setListenMode("CLIENT");
            entry.setDestinationMode("SERVER");
            entry.setListenReconnectDelayMillis(100);

            TcpRelay relay = new TcpRelay(entry);
            relay.start();

            try {
                producerServer.setSoTimeout(3000);
                Socket firstProducerSide = producerServer.accept();
                firstProducerSide.setSoTimeout(3000);
                Socket firstDestinationSide = new Socket("127.0.0.1", destinationServerPort);
                firstDestinationSide.setSoTimeout(3000);

                // Push one byte through to confirm splicing has actually started (a disconnect
                // is only observable once something is reading from the socket) before closing.
                firstProducerSide.getOutputStream().write('x');
                firstProducerSide.getOutputStream().flush();
                readFully(firstDestinationSide.getInputStream(), new byte[1], 1);

                firstProducerSide.close();

                // The relay should notice the disconnect and reconnect.
                Socket secondProducerSide = producerServer.accept();
                assertThat(secondProducerSide).isNotNull();
                secondProducerSide.close();
                firstDestinationSide.close();
            } finally {
                relay.stop();
                mirror.close();
            }
        }
    }

    private static class EchoServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private volatile boolean running = true;

        EchoServer() throws IOException {
            serverSocket = new ServerSocket(0);
            Thread thread = new Thread(this::acceptLoop, "test-echo-accept");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void acceptLoop() {
            while (running) {
                Socket connection;
                try {
                    connection = serverSocket.accept();
                } catch (IOException e) {
                    return;
                }
                Thread handler = new Thread(() -> handle(connection), "test-echo-handle");
                handler.setDaemon(true);
                handler.start();
            }
        }

        private void handle(Socket connection) {
            try (connection) {
                InputStream in = connection.getInputStream();
                OutputStream out = connection.getOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (IOException ignored) {
            }
        }

        @Override
        public void close() throws IOException {
            running = false;
            serverSocket.close();
        }
    }

    private static class CapturingServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final List<Socket> acceptedConnections = Collections.synchronizedList(new ArrayList<>());
        private final ByteArrayOutputStream received = new ByteArrayOutputStream();
        private final Object lock = new Object();
        private volatile boolean running = true;

        CapturingServer() throws IOException {
            serverSocket = new ServerSocket(0);
            Thread thread = new Thread(this::acceptLoop, "test-mirror-accept");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        int acceptedConnectionCount() {
            return acceptedConnections.size();
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
                } catch (IOException e) {
                    return;
                }
                acceptedConnections.add(connection);
                Thread reader = new Thread(() -> readLoop(connection), "test-mirror-read");
                reader.setDaemon(true);
                reader.start();
            }
        }

        private void readLoop(Socket connection) {
            try {
                InputStream in = connection.getInputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    synchronized (lock) {
                        received.write(buffer, 0, read);
                    }
                }
            } catch (IOException ignored) {
            }
        }

        @Override
        public void close() throws IOException {
            running = false;
            serverSocket.close();
            for (Socket connection : acceptedConnections) {
                try {
                    connection.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
