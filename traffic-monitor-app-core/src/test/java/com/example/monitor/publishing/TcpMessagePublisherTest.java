package com.example.monitor.publishing;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TcpMessagePublisherTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void send_transmitsPayloadToTargetHostAndPort() throws Exception {
        try (ServerSocket receiver = new ServerSocket(0)) {
            int port = receiver.getLocalPort();
            byte[] payload = {1, 2, 3, 4};

            new TcpMessagePublisher(meterRegistry).send("localhost", port, payload);

            try (Socket accepted = receiver.accept()) {
                byte[] received = accepted.getInputStream().readNBytes(payload.length);
                assertThat(received).isEqualTo(payload);
            }
            assertThat(meterRegistry.counter("network_monitor.messages.sent", "transport", "TCP").count()).isEqualTo(1.0);
        }
    }

    @Test
    void send_whenHostUnresolvable_throwsIllegalStateException() {
        assertThatThrownBy(() -> new TcpMessagePublisher(meterRegistry).send("this.host.does.not.exist.invalid", 7001, new byte[] {1}))
                .isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry.counter("network_monitor.messages.send_errors", "transport", "TCP").count()).isEqualTo(1.0);
    }
}
