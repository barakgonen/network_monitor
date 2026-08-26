package com.example.tester.udp;

import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UdpPublisherTest {

    private final UdpPublisher publisher = new UdpPublisher();

    @Test
    void send_withoutSocket_usesEphemeralSourcePort() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(0)) {
            receiver.setSoTimeout(2000);
            byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);

            publisher.send("127.0.0.1", receiver.getLocalPort(), payload);

            byte[] buffer = new byte[1024];
            DatagramPacket received = new DatagramPacket(buffer, buffer.length);
            receiver.receive(received);

            assertThat(new String(received.getData(), 0, received.getLength(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }
    }

    @Test
    void send_withSharedSocket_usesThatSocketsSourcePort() throws Exception {
        try (DatagramSocket sharedSocket = new DatagramSocket(0);
             DatagramSocket receiver = new DatagramSocket(0)) {
            receiver.setSoTimeout(2000);
            byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);

            publisher.send(sharedSocket, "127.0.0.1", receiver.getLocalPort(), payload);

            byte[] buffer = new byte[1024];
            DatagramPacket received = new DatagramPacket(buffer, buffer.length);
            receiver.receive(received);

            // The whole point of this overload: the packet's observed source port must be the
            // caller-supplied socket's bound port, not a fresh ephemeral one.
            assertThat(received.getPort()).isEqualTo(sharedSocket.getLocalPort());
            assertThat(new String(received.getData(), 0, received.getLength(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }
    }

    @Test
    void send_toUnresolvableHost_throwsIllegalStateException() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            assertThatThrownBy(() -> publisher.send(socket, "this-host-does-not-resolve.invalid", 12345, "x".getBytes()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
