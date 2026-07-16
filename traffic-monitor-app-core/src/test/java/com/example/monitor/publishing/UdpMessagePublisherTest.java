package com.example.monitor.publishing;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UdpMessagePublisherTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void send_transmitsPayloadToTargetHostAndPort() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(0)) {
            int port = receiver.getLocalPort();
            byte[] payload = {1, 2, 3, 4};

            new UdpMessagePublisher(meterRegistry).send("localhost", port, payload);

            byte[] buffer = new byte[16];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiver.setSoTimeout(2000);
            receiver.receive(packet);

            byte[] received = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), 0, received, 0, packet.getLength());
            assertThat(received).isEqualTo(payload);
            assertThat(meterRegistry.counter("network_monitor.messages.sent", "transport", "UDP").count()).isEqualTo(1.0);
        }
    }

    @Test
    void send_whenHostUnresolvable_throwsIllegalStateException() {
        assertThatThrownBy(() -> new UdpMessagePublisher(meterRegistry).send("this.host.does.not.exist.invalid", 7001, new byte[] {1}))
                .isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry.counter("network_monitor.messages.send_errors", "transport", "UDP").count()).isEqualTo(1.0);
    }
}
