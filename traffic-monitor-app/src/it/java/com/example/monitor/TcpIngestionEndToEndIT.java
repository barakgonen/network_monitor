package com.example.monitor;

import com.example.monitor.interfaces.InterfaceConfigureRequest;
import com.example.monitor.interfaces.InterfaceStatusDto;
import com.example.monitor.model.ObservedMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Candy is the interface configured for TCP by default (see traffic-tool-test.yml), so it's used
 * here for general TCP framing/error-handling coverage. The last test exercises the actual new
 * capability under test in this class: reconfiguring an interface's protocol at runtime and
 * having it work end-to-end.
 */
class TcpIngestionEndToEndIT extends AbstractIntegrationTestBase {

    @AfterEach
    void restoreFruitToDefaultUdpConfig() {
        // The Spring context (and its InterfaceConfig instances) is shared/cached across IT
        // classes, so the runtime protocol reconfiguration test below must not leak state into
        // other tests even if it fails partway through.
        restTemplate.postForEntity(httpUrl("/api/interfaces/fruit/stop"), null, InterfaceStatusDto[].class);
        restTemplate.postForEntity(
                httpUrl("/api/interfaces/fruit/configure"),
                new InterfaceConfigureRequest(fruitPort, "UDP", "SERVER", null),
                InterfaceStatusDto[].class);
        restTemplate.postForEntity(httpUrl("/api/interfaces/fruit/start"), null, InterfaceStatusDto[].class);
    }

    @Test
    void sendingValidCandyPayload_landsInRecentMessageStore() throws Exception {
        sendTcp(candyPort, TestProtocolPayloads.candy("TCP Truffle", 120.0));

        ObservedMessage message = awaitStoreContains(
                m -> "Candy".equals(m.messageType()) && "TCP Truffle".equals(m.body().get("name")));

        assertThat(message.transportProtocol()).isEqualTo("TCP");
        assertThat(message.interfaceName()).isEqualTo("Candy Interface");
        assertThat(message.parseError()).isNull();
        assertThat(message.body().get("calories")).isEqualTo(120.0);
    }

    @Test
    void sendingPayloadWithUnknownOpcode_landsInStoreWithParseError() throws Exception {
        sendTcp(candyPort, TestProtocolPayloads.rawHeaderOnly(9999, 0));

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
        sendTcp(candyPort, TestProtocolPayloads.withBodyLengthMismatch());

        sendTcp(candyPort, TestProtocolPayloads.candy("Recovery Bar", 90.0));

        ObservedMessage message = awaitStoreContains(
                m -> "Candy".equals(m.messageType()) && "Recovery Bar".equals(m.body().get("name")));

        assertThat(message.transportProtocol()).isEqualTo("TCP");
        assertThat(message.parseError()).isNull();
    }

    @Test
    void multipleMessagesSentOnOnePersistentConnection_allLandInStore() throws Exception {
        sendTcpMultiple(candyPort,
                TestProtocolPayloads.candy("multi-1", 1.0),
                TestProtocolPayloads.candy("multi-2", 2.0),
                TestProtocolPayloads.candy("multi-3", 3.0));

        awaitStoreContains(m -> "Candy".equals(m.messageType()) && "multi-1".equals(m.body().get("name")));
        awaitStoreContains(m -> "Candy".equals(m.messageType()) && "multi-2".equals(m.body().get("name")));
        awaitStoreContains(m -> "Candy".equals(m.messageType()) && "multi-3".equals(m.body().get("name")));
    }

    @Test
    void connectionMetrics_trackAcceptedConnectionsAndActiveGauge() throws Exception {
        double acceptedBefore = meterRegistry.counter("network_monitor.tcp.connections.accepted", "port", String.valueOf(candyPort)).count();

        sendTcp(candyPort, TestProtocolPayloads.candy("metrics-candy", 10.0));

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            double acceptedAfter = meterRegistry.counter("network_monitor.tcp.connections.accepted", "port", String.valueOf(candyPort)).count();
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
                "transport", "TCP", "interfaceName", "Candy Interface", "parseError", "false").count();

        sendTcp(candyPort, TestProtocolPayloads.candy("metrics-farm", 1.0));
        awaitStoreContains(m -> "Candy".equals(m.messageType()) && "metrics-farm".equals(m.body().get("name")));

        double after = meterRegistry.counter("network_monitor.messages.received",
                "transport", "TCP", "interfaceName", "Candy Interface", "parseError", "false").count();

        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void reconfiguringInterfaceToTcpAtRuntime_thenSendingOverTcp_decodesSuccessfully() throws Exception {
        restTemplate.postForEntity(httpUrl("/api/interfaces/fruit/stop"), null, InterfaceStatusDto[].class);

        ResponseEntity<InterfaceStatusDto[]> configured = restTemplate.postForEntity(
                httpUrl("/api/interfaces/fruit/configure"),
                new InterfaceConfigureRequest(fruitPort, "TCP", "SERVER", null),
                InterfaceStatusDto[].class);

        InterfaceStatusDto fruitStatus = statusFor(configured.getBody(), "fruit");
        assertThat(fruitStatus.protocol()).isEqualTo("TCP");
        assertThat(fruitStatus.listening()).isFalse();

        restTemplate.postForEntity(httpUrl("/api/interfaces/fruit/start"), null, InterfaceStatusDto[].class);

        sendTcp(fruitPort, TestProtocolPayloads.orange("Reconfigured Farm", (byte) 1));

        ObservedMessage message = awaitStoreContains(
                m -> "Orange".equals(m.messageType()) && "Reconfigured Farm".equals(m.body().get("sourceFarm")));

        assertThat(message.transportProtocol()).isEqualTo("TCP");
        assertThat(message.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(message.parseError()).isNull();
    }

    @Test
    void reconfiguringInterfaceToTcpClientMode_connectsOutAndDecodesSuccessfully() throws Exception {
        try (ServerSocket remoteServer = new ServerSocket(0)) {
            remoteServer.setSoTimeout(5000);

            restTemplate.postForEntity(httpUrl("/api/interfaces/fruit/stop"), null, InterfaceStatusDto[].class);

            ResponseEntity<InterfaceStatusDto[]> configured = restTemplate.postForEntity(
                    httpUrl("/api/interfaces/fruit/configure"),
                    new InterfaceConfigureRequest(remoteServer.getLocalPort(), "TCP", "CLIENT", "localhost"),
                    InterfaceStatusDto[].class);

            InterfaceStatusDto fruitStatus = statusFor(configured.getBody(), "fruit");
            assertThat(fruitStatus.protocol()).isEqualTo("TCP");
            assertThat(fruitStatus.mode()).isEqualTo("CLIENT");
            assertThat(fruitStatus.host()).isEqualTo("localhost");

            restTemplate.postForEntity(httpUrl("/api/interfaces/fruit/start"), null, InterfaceStatusDto[].class);

            try (Socket accepted = remoteServer.accept()) {
                accepted.getOutputStream().write(TestProtocolPayloads.orange("Client Mode Farm", (byte) 1));
                accepted.getOutputStream().flush();

                ObservedMessage message = awaitStoreContains(
                        m -> "Orange".equals(m.messageType()) && "Client Mode Farm".equals(m.body().get("sourceFarm")));

                assertThat(message.transportProtocol()).isEqualTo("TCP");
                assertThat(message.interfaceName()).isEqualTo("Fruit Interface");
                assertThat(message.parseError()).isNull();
            }
        }
    }

    private static InterfaceStatusDto statusFor(InterfaceStatusDto[] statuses, String key) {
        for (InterfaceStatusDto status : statuses) {
            if (key.equals(status.key())) {
                return status;
            }
        }
        throw new AssertionError("No status found for interface " + key);
    }
}
