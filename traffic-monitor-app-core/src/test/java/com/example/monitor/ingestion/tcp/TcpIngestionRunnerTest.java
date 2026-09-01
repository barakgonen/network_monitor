package com.example.monitor.ingestion.tcp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.ingestion.MessageIngestionPipeline;
import com.example.monitor.interfaces.InterfaceRuntimeRegistry;
import com.example.monitor.model.ObservedMessage;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.envelope.DefaultEnvelopeHeader;
import com.example.trafficconfig.InterfaceConfig;
import com.example.trafficconfig.TrafficToolConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the parts of {@link TcpIngestionRunner} that the app-level *EndToEndIT suite
 * (traffic-monitor-app) only ever exercises on the happy path: framing edge cases, bind failure,
 * and the CLIENT-mode reconnect loop.
 */
@ExtendWith(MockitoExtension.class)
class TcpIngestionRunnerTest {

    private static final String KEY = "tcpTest";

    @Mock
    private MessageIngestionPipeline pipeline;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private TcpIngestionRunner runner;
    private TrafficMonitorProperties properties;
    private InterfaceConfig interfaceConfig;

    @BeforeEach
    void setUp() {
        properties = new TrafficMonitorProperties();
        properties.getTcp().setMaxBodyLengthBytes(1024);
        properties.getTcp().setClientReconnectDelayMs(50);
        properties.getTcp().setClientConnectTimeoutMs(500);

        interfaceConfig = new InterfaceConfig();
        interfaceConfig.setKey(KEY);
        interfaceConfig.setName("TCP Test Interface");
        interfaceConfig.setProtocol("TCP");

        TrafficToolConfig trafficToolConfig = new TrafficToolConfig();
        trafficToolConfig.setInterfaces(List.of(interfaceConfig));

        runner = new TcpIngestionRunner(
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

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static byte[] header(int bodyLength) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        DefaultEnvelopeHeader header = new DefaultEnvelopeHeader();
        header.setOpcode(1);
        header.setSendTimeEpochMillis(Instant.now().toEpochMilli());
        header.setBodyLength(bodyLength);
        header.toByteArray(buffer);
        return buffer.array();
    }

    @Test
    void startInterface_serverMode_portAlreadyBound_throwsIllegalStateException() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            interfaceConfig.setPort(occupied.getLocalPort());

            assertThatThrownBy(() -> runner.startInterface(interfaceConfig))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void startInterface_serverMode_validMessage_reachesPipeline() throws Exception {
        interfaceConfig.setPort(findFreePort());
        when(pipeline.ingestForInterface(any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(observedMessage());

        runner.startInterface(interfaceConfig);

        try (Socket client = new Socket("localhost", interfaceConfig.getPort())) {
            byte[] body = {1, 2, 3};
            client.getOutputStream().write(header(body.length));
            client.getOutputStream().write(body);
            client.getOutputStream().flush();

            await().untilAsserted(() ->
                    org.mockito.Mockito.verify(pipeline).ingestForInterface(any(), any(), any(), any(Integer.class), any(), any()));
        }
    }

    @Test
    void handleConnection_negativeBodyLength_closesConnectionWithoutIngesting() throws Exception {
        interfaceConfig.setPort(findFreePort());
        runner.startInterface(interfaceConfig);

        try (Socket client = new Socket("localhost", interfaceConfig.getPort())) {
            client.getOutputStream().write(header(-1));
            client.getOutputStream().flush();

            await().untilAsserted(() -> assertThat(client.getInputStream().read()).isEqualTo(-1));
        }

        verifyNoInteractions(pipeline);
        assertThat(meterRegistry.counter("network_monitor.tcp.connections.errors",
                "port", String.valueOf(interfaceConfig.getPort())).count()).isEqualTo(1.0);
    }

    @Test
    void handleConnection_bodyLengthExceedsConfiguredMax_closesConnectionWithoutIngesting() throws Exception {
        interfaceConfig.setPort(findFreePort());
        runner.startInterface(interfaceConfig);

        try (Socket client = new Socket("localhost", interfaceConfig.getPort())) {
            client.getOutputStream().write(header(properties.getTcp().getMaxBodyLengthBytes() + 1));
            client.getOutputStream().flush();

            await().untilAsserted(() -> assertThat(client.getInputStream().read()).isEqualTo(-1));
        }

        verifyNoInteractions(pipeline);
    }

    @Test
    void handleConnection_truncatedStream_doesNotCrashAcceptLoop() throws Exception {
        interfaceConfig.setPort(findFreePort());
        runner.startInterface(interfaceConfig);

        try (Socket client = new Socket("localhost", interfaceConfig.getPort())) {
            client.getOutputStream().write(new byte[] {1, 2, 3});
            client.getOutputStream().flush();
        }

        verifyNoInteractions(pipeline);

        when(pipeline.ingestForInterface(any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(observedMessage());

        try (Socket client = new Socket("localhost", interfaceConfig.getPort())) {
            byte[] body = {9};
            client.getOutputStream().write(header(body.length));
            client.getOutputStream().write(body);
            client.getOutputStream().flush();

            await().untilAsserted(() ->
                    org.mockito.Mockito.verify(pipeline).ingestForInterface(any(), any(), any(), any(Integer.class), any(), any()));
        }
    }

    @Test
    void startInterface_clientMode_reconnectsUntilServerBecomesAvailable() throws Exception {
        int port = findFreePort();
        interfaceConfig.setPort(port);
        interfaceConfig.setMode("CLIENT");
        interfaceConfig.setHost("127.0.0.1");
        when(pipeline.ingestForInterface(any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(observedMessage());

        runner.startInterface(interfaceConfig);

        // Generous bound (not just Awaitility's 10s default): a busy/shared CI runner can add
        // enough scheduling/JIT jitter that even one fast local TCP connect-refuse-retry cycle
        // occasionally doesn't land inside 10s - seen flake in CI even though the analogous
        // reconnect assertion below always passed quickly locally.
        await().atMost(java.time.Duration.ofSeconds(30)).until(() ->
                meterRegistry.counter("network_monitor.tcp.client.reconnect.attempts",
                        "port", String.valueOf(port)).count() >= 1.0);

        try (ServerSocket server = new ServerSocket(port)) {
            try (Socket accepted = server.accept()) {
                byte[] body = {7, 7};
                accepted.getOutputStream().write(header(body.length));
                accepted.getOutputStream().write(body);
                accepted.getOutputStream().flush();

                await().untilAsserted(() -> org.mockito.Mockito.verify(pipeline)
                        .ingestForInterface(any(), any(), any(), any(Integer.class), any(), any()));
            }
        }
    }

    @Test
    void stopInterface_duringClientReconnectLoop_stopsRetrying() throws Exception {
        interfaceConfig.setPort(findFreePort());
        interfaceConfig.setMode("CLIENT");
        interfaceConfig.setHost("127.0.0.1");

        runner.startInterface(interfaceConfig);

        await().atMost(java.time.Duration.ofSeconds(30)).until(() ->
                meterRegistry.counter("network_monitor.tcp.client.reconnect.attempts",
                        "port", String.valueOf(interfaceConfig.getPort())).count() >= 1.0);

        runner.stopInterface(KEY);

        double countAfterStop = meterRegistry.counter("network_monitor.tcp.client.reconnect.attempts",
                "port", String.valueOf(interfaceConfig.getPort())).count();

        await().during(java.time.Duration.ofMillis(300)).until(() ->
                meterRegistry.counter("network_monitor.tcp.client.reconnect.attempts",
                        "port", String.valueOf(interfaceConfig.getPort())).count() == countAfterStop);
    }

    private static ObservedMessage observedMessage() {
        return new ObservedMessage(
                "id", Instant.now(), "TCP", "127.0.0.1:1", 1, "TCP Test Interface",
                "type", Map.of(), Map.of(), 3, null, null, null);
    }
}
