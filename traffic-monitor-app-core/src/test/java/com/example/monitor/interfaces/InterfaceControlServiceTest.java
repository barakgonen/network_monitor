package com.example.monitor.interfaces;

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

@ExtendWith(MockitoExtension.class)
class InterfaceControlServiceTest {

    @Mock
    private UdpIngestionRunner udpIngestionRunner;

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
        service = new InterfaceControlService(runtimeRegistry, udpIngestionRunner);
    }

    @Test
    void start_delegatesToUdpIngestionRunner() {
        service.start("rada");

        verify(udpIngestionRunner).startInterface(radaConfig);
    }

    @Test
    void stop_delegatesToUdpIngestionRunner() {
        service.stop("rada");

        verify(udpIngestionRunner).stopInterface("rada");
    }

    @Test
    void start_withUnknownKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.start("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown");
    }

    @Test
    void start_withNonUdpProtocol_throwsIllegalArgumentException() {
        radaConfig.setProtocol("TCP");

        assertThatThrownBy(() -> service.start("rada"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UDP");
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
