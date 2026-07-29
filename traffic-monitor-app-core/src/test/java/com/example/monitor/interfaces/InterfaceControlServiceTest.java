package com.example.monitor.interfaces;

import com.example.monitor.ingestion.tcp.TcpIngestionRunner;
import com.example.monitor.ingestion.udp.UdpIngestionRunner;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InterfaceControlServiceTest {

    @Mock
    private UdpIngestionRunner udpIngestionRunner;

    @Mock
    private TcpIngestionRunner tcpIngestionRunner;

    private InterfaceRuntimeRegistry runtimeRegistry;
    private InterfaceControlService service;
    private InterfaceConfig radaConfig;

    @BeforeEach
    void setUp() {
        radaConfig = new InterfaceConfig();
        radaConfig.setKey("rada");
        radaConfig.setName("Rada Interface");
        radaConfig.setPort(5050);
        radaConfig.setProtocol("UDP");

        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(radaConfig));

        runtimeRegistry = new InterfaceRuntimeRegistry(config);
        service = new InterfaceControlService(runtimeRegistry, udpIngestionRunner, tcpIngestionRunner);
    }

    @Test
    void start_withUdpProtocol_delegatesToUdpIngestionRunner() {
        service.start("rada");

        verify(udpIngestionRunner).startInterface(radaConfig);
        verifyNoInteractions(tcpIngestionRunner);
    }

    @Test
    void stop_withUdpProtocol_delegatesToUdpIngestionRunner() {
        service.stop("rada");

        verify(udpIngestionRunner).stopInterface("rada");
        verifyNoInteractions(tcpIngestionRunner);
    }

    @Test
    void start_withTcpProtocol_delegatesToTcpIngestionRunner() {
        radaConfig.setProtocol("TCP");

        service.start("rada");

        verify(tcpIngestionRunner).startInterface(radaConfig);
        verifyNoInteractions(udpIngestionRunner);
    }

    @Test
    void stop_withTcpProtocol_delegatesToTcpIngestionRunner() {
        radaConfig.setProtocol("TCP");

        service.stop("rada");

        verify(tcpIngestionRunner).stopInterface("rada");
        verifyNoInteractions(udpIngestionRunner);
    }

    @Test
    void start_withUnknownKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.start("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown");
    }

    @Test
    void start_withUnsupportedProtocol_throwsIllegalArgumentException() {
        radaConfig.setProtocol("SCTP");

        assertThatThrownBy(() -> service.start("rada"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SCTP");
    }

    @Test
    void configure_whileNotListening_updatesPortAndProtocol() {
        service.configure("rada", 6050, "TCP", "SERVER", null);

        assertThat(radaConfig.getPort()).isEqualTo(6050);
        assertThat(radaConfig.getProtocol()).isEqualTo("TCP");
    }

    @Test
    void configure_withClientModeAndHost_updatesModeAndHost() {
        service.configure("rada", 6050, "TCP", "CLIENT", "remote-host");

        assertThat(radaConfig.getMode()).isEqualTo("CLIENT");
        assertThat(radaConfig.getHost()).isEqualTo("remote-host");
    }

    @Test
    void configure_withClientModeAndUdpProtocol_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.configure("rada", 6050, "UDP", "CLIENT", "remote-host"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT");
    }

    @Test
    void configure_withClientModeAndBlankHost_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.configure("rada", 6050, "TCP", "CLIENT", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    void configure_whileListening_throwsIllegalStateException() {
        runtimeRegistry.state("rada").orElseThrow().setListening(true);

        assertThatThrownBy(() -> service.configure("rada", 6050, "TCP", "SERVER", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void statuses_reflectsRuntimeRegistryState() {
        runtimeRegistry.state("rada").orElseThrow().setListening(true);

        List<InterfaceStatusDto> statuses = service.statuses();

        assertThat(statuses).hasSize(1);
        assertThat(statuses.get(0).key()).isEqualTo("rada");
        assertThat(statuses.get(0).listening()).isTrue();
    }
}
