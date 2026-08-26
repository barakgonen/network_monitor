package com.example.proxy.integration;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import com.example.destination.listener.TcpEchoListener;
import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.example.proxy.relay.TcpRelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real {@link TcpRelay} wired against a real {@link TcpEchoListener} (traffic-destination-app),
 * proving the persistent-mirror-connection framing assumption
 * (see {@code com.example.proxy.mirror.TcpMirrorConnection}) holds against the actual
 * destination-app echo behavior, not just a hand-rolled test double.
 */
class TcpRelayIT {

    private TcpRelay relay;
    private TcpEchoListener destinationListener;

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

    private static int readFully(InputStream in, byte[] buffer, int expectedLength) throws Exception {
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

    @Test
    void realDestinationAppEchoesCandyMessage_relayedAndMirroredCorrectly() throws Exception {
        int destinationPort = freePort();
        InterfaceEntry destinationConfig = new InterfaceEntry();
        destinationConfig.setKey("candy");
        destinationConfig.setProtocol("TCP");
        destinationConfig.setPort(destinationPort);
        destinationConfig.setReplyMode(ReplyMode.ECHO);
        destinationListener = new TcpEchoListener(destinationConfig);
        destinationListener.start();

        try (ServerSocket mirrorServer = new ServerSocket(0)) {
            int listenPort = freePort();
            RelayEntry relayEntry = new RelayEntry();
            relayEntry.setKey("candy");
            relayEntry.setProtocol("TCP");
            relayEntry.setListen(endpoint("0.0.0.0", listenPort));
            relayEntry.setDestination(endpoint("127.0.0.1", destinationPort));
            relayEntry.setMirror(endpoint("127.0.0.1", mirrorServer.getLocalPort()));
            relay = new TcpRelay(relayEntry);
            relay.start();

            try (Socket client = new Socket("127.0.0.1", listenPort)) {
                client.setSoTimeout(3000);
                OutputStream out = client.getOutputStream();
                InputStream in = client.getInputStream();

                byte[] payload = "chocolate-bar".getBytes();
                out.write(payload);
                out.flush();

                byte[] buffer = new byte[1024];
                int read = readFully(in, buffer, payload.length);
                assertThat(new String(buffer, 0, read)).isEqualTo("chocolate-bar");
            }

            mirrorServer.setSoTimeout(3000);
            try (Socket mirrorConnection = mirrorServer.accept()) {
                mirrorConnection.setSoTimeout(3000);
                String expected = "chocolate-barchocolate-bar";
                byte[] buffer = new byte[1024];
                int read = readFully(mirrorConnection.getInputStream(), buffer, expected.length());
                assertThat(new String(buffer, 0, read)).isEqualTo(expected);
            }
        }
    }
}
