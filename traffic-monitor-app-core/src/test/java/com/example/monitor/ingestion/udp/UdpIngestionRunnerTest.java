package com.example.monitor.ingestion.udp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.ingestion.MessageIngestionPipeline;
import com.example.monitor.interfaces.InterfaceRuntimeRegistry;
import com.example.monitor.model.ObservedMessage;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.trafficconfig.InterfaceConfig;
import com.example.trafficconfig.TrafficToolConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UdpIngestionRunner} behavior the app-level *EndToEndIT suite
 * (traffic-monitor-app) only exercises on the happy path: bind failure and stopping a socket
 * while a receive is in flight.
 */
@ExtendWith(MockitoExtension.class)
class UdpIngestionRunnerTest {

    private static final String KEY = "udpTest";

    @Mock
    private MessageIngestionPipeline pipeline;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private UdpIngestionRunner runner;
    private InterfaceConfig interfaceConfig;

    @BeforeEach
    void setUp() {
        TrafficMonitorProperties properties = new TrafficMonitorProperties();

        interfaceConfig = new InterfaceConfig();
        interfaceConfig.setKey(KEY);
        interfaceConfig.setName("UDP Test Interface");
        interfaceConfig.setProtocol("UDP");

        TrafficToolConfig trafficToolConfig = new TrafficToolConfig();
        trafficToolConfig.setInterfaces(List.of(interfaceConfig));

        runner = new UdpIngestionRunner(
                properties,
                pipeline,
                meterRegistry,
                trafficToolConfig,
                Map.of(KEY, new MessageDefinitionRegistry(List.of())),
                new InterfaceRuntimeRegistry(trafficToolConfig));
    }

    @AfterEach
    void tearDown() {
        runner.stopInterface(KEY);
    }

    private static int findFreePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void startInterface_portAlreadyBound_throwsIllegalStateException() throws Exception {
        try (DatagramSocket occupied = new DatagramSocket(0)) {
            interfaceConfig.setPort(occupied.getLocalPort());

            assertThatThrownBy(() -> runner.startInterface(interfaceConfig))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void startInterface_validDatagram_reachesPipeline() throws Exception {
        interfaceConfig.setPort(findFreePort());
        when(pipeline.ingestForInterface(any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(observedMessage());

        runner.startInterface(interfaceConfig);

        byte[] body = {5, 6, 7};
        try (DatagramSocket client = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(
                    body, body.length, InetAddress.getLoopbackAddress(), interfaceConfig.getPort());
            client.send(packet);
        }

        await().untilAsserted(() ->
                verify(pipeline).ingestForInterface(any(), any(), any(), any(Integer.class), any(), any()));
    }

    @Test
    void stopInterface_whileBlockedInReceive_closesSocketWithoutLoggingListenerError() throws Exception {
        interfaceConfig.setPort(findFreePort());
        runner.startInterface(interfaceConfig);

        // Give the background executor a moment to actually enter socket.receive() before we
        // close the socket out from under it.
        Thread.sleep(100);

        runner.stopInterface(KEY);

        await().during(java.time.Duration.ofMillis(300)).until(() ->
                meterRegistry.find("network_monitor.udp.listener.errors").counter() == null);

        try (DatagramSocket probe = new DatagramSocket(interfaceConfig.getPort())) {
            assertThat(probe.getLocalPort()).isEqualTo(interfaceConfig.getPort());
        }
    }

    private static ObservedMessage observedMessage() {
        return new ObservedMessage(
                "id", Instant.now(), "UDP", "127.0.0.1:1", 1, "UDP Test Interface",
                "type", Map.of(), Map.of(), 3, null, null, null);
    }
}
