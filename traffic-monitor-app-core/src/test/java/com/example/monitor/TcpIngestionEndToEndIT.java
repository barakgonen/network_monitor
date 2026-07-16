package com.example.monitor;

import com.example.monitor.model.ObservedMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TcpIngestionEndToEndIT extends AbstractIntegrationTestBase {

    @Test
    void sendingValidOrangePayload_landsInRecentMessageStore() throws Exception {
        sendTcp(tcpFruitPort, TestProtocolPayloads.orange("TCP Acres", (byte) 1));

        ObservedMessage message = awaitStoreContains(
                m -> "Orange".equals(m.messageType()) && "TCP Acres".equals(m.body().get("sourceFarm")));

        assertThat(message.transportProtocol()).isEqualTo("TCP");
        assertThat(message.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(message.parseError()).isNull();
        assertThat(message.body().get("freshness")).isEqualTo("very_fresh");
    }

    @Test
    void sendingValidBananaPayload_landsInStore() throws Exception {
        sendTcp(tcpFruitPort, TestProtocolPayloads.banana("green-tcp", 42.0));

        ObservedMessage message = awaitStoreContains(
                m -> "Banana".equals(m.messageType()) && "green-tcp".equals(m.body().get("color")));

        assertThat(message.transportProtocol()).isEqualTo("TCP");
        assertThat(message.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(message.parseError()).isNull();
        assertThat(message.body().get("weight")).isEqualTo(42.0);
    }

    @Test
    void sendingValidTemperatureReadingPayload_toWeatherPort_landsInStore() throws Exception {
        sendTcp(tcpWeatherPort, TestProtocolPayloads.temperatureReading("station-tcp", 12.5, (byte) 2));

        ObservedMessage message = awaitStoreContains(
                m -> "TemperatureReading".equals(m.messageType()) && "station-tcp".equals(m.body().get("stationId")));

        assertThat(message.transportProtocol()).isEqualTo("TCP");
        assertThat(message.interfaceName()).isEqualTo("Weather Interface");
        assertThat(message.parseError()).isNull();
        assertThat(message.body().get("condition")).isEqualTo("cloudy");
    }

    @Test
    void sendingPayloadWithUnknownOpcode_landsInStoreWithParseError() throws Exception {
        sendTcp(tcpFruitPort, TestProtocolPayloads.rawHeaderOnly(9999, 0));

        ObservedMessage message = awaitStoreContains(
                m -> "TCP".equals(m.transportProtocol()) && m.parseError() != null && "Unknown".equals(m.interfaceName()));

        assertThat(message.parseError()).contains("Unknown opcode");
    }

    @Test
    void sendingTruncatedBodyPayload_doesNotCrashServerAndSubsequentMessagesStillWork() throws Exception {
        // A header declaring a bodyLength the connection doesn't actually deliver before closing.
        // Unlike UDP (where a whole malformed datagram is captured and decoded as one message),
        // a TCP stream has no boundary until the promised body bytes arrive - so this manifests as
        // a connection-level read failure, not an ObservedMessage with parseError. The meaningful
        // assertion is that the accept loop keeps working for the next connection.
        sendTcp(tcpFruitPort, TestProtocolPayloads.withBodyLengthMismatch());

        sendTcp(tcpFruitPort, TestProtocolPayloads.orange("Recovery Farm", (byte) 1));

        ObservedMessage message = awaitStoreContains(
                m -> "Orange".equals(m.messageType()) && "Recovery Farm".equals(m.body().get("sourceFarm")));

        assertThat(message.transportProtocol()).isEqualTo("TCP");
        assertThat(message.parseError()).isNull();
    }

    @Test
    void multipleMessagesSentOnOnePersistentConnection_allLandInStore() throws Exception {
        sendTcpMultiple(tcpFruitPort,
                TestProtocolPayloads.orange("multi-farm", (byte) 1),
                TestProtocolPayloads.banana("multi-color", 5.0),
                TestProtocolPayloads.ping(99));

        awaitStoreContains(m -> "Orange".equals(m.messageType()) && "multi-farm".equals(m.body().get("sourceFarm")));
        awaitStoreContains(m -> "Banana".equals(m.messageType()) && "multi-color".equals(m.body().get("color")));
        awaitStoreContains(m -> "Ping".equals(m.messageType()) && Integer.valueOf(99).equals(m.body().get("sequence")));
    }

    @Test
    void connectionMetrics_trackAcceptedConnectionsAndActiveGauge() throws Exception {
        double acceptedBefore = meterRegistry.counter("network_monitor.tcp.connections.accepted", "port", String.valueOf(tcpFruitPort)).count();

        sendTcp(tcpFruitPort, TestProtocolPayloads.ping(1));

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            double acceptedAfter = meterRegistry.counter("network_monitor.tcp.connections.accepted", "port", String.valueOf(tcpFruitPort)).count();
            assertThat(acceptedAfter).isEqualTo(acceptedBefore + 1);
        });

        assertThat(meterRegistry.find("network_monitor.tcp.connections.active").gauge()).isNotNull();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Double active = meterRegistry.find("network_monitor.tcp.connections.active").gauge().value();
            assertThat(active).isGreaterThanOrEqualTo(0.0);
        });
    }

    @Test
    void messagesReceivedMetric_incrementsForTcpTraffic() throws Exception {
        double before = meterRegistry.counter("network_monitor.messages.received",
                "transport", "TCP", "interfaceName", "Fruit Interface", "parseError", "false").count();

        sendTcp(tcpFruitPort, TestProtocolPayloads.orange("metrics-farm", (byte) 1));
        awaitStoreContains(m -> "Orange".equals(m.messageType()) && "metrics-farm".equals(m.body().get("sourceFarm")));

        double after = meterRegistry.counter("network_monitor.messages.received",
                "transport", "TCP", "interfaceName", "Fruit Interface", "parseError", "false").count();

        assertThat(after).isEqualTo(before + 1);
    }
}
