package com.example.monitor;

import com.example.monitor.interfaces.InterfaceStatusDto;
import com.example.monitor.model.ObservedMessage;
import com.example.schemacore.ReflectiveStructCodec;
import com.example.schemas.rada.messages.RadaStatus;
import com.example.schemas.rada.struct.RadaHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RadaInterfaceEndToEndIT extends AbstractIntegrationTestBase {

    private static final int RADA_PORT = 25050;
    private static final int RADA_STATUS_OPCODE = 3;

    @AfterEach
    void stopRadaInterface() {
        restTemplate.postForEntity(httpUrl("/api/interfaces/rada/stop"), null, InterfaceStatusDto[].class);
    }

    @Test
    void startingInterface_thenSendingRadaStatus_landsInStoreAndReportsListening() throws Exception {
        restTemplate.postForEntity(httpUrl("/api/interfaces/rada/start"), null, InterfaceStatusDto[].class);

        InterfaceStatusDto[] statuses =
                restTemplate.getForEntity(httpUrl("/api/interfaces"), InterfaceStatusDto[].class).getBody();
        assertThat(statuses).extracting(InterfaceStatusDto::key).contains("rada");
        assertThat(statuses)
                .filteredOn(dto -> "rada".equals(dto.key()))
                .allMatch(InterfaceStatusDto::listening);

        sendUdp(RADA_PORT, radaStatusPayload());

        ObservedMessage message = awaitStoreContains(m -> "RadaStatus".equals(m.messageType()));

        assertThat(message.interfaceName()).isEqualTo("Rada Interface");
        assertThat(message.parseError()).isNull();
        assertThat(message.header().get("msgType")).isEqualTo(3L);

        InterfaceStatusDto[] afterMessage =
                restTemplate.getForEntity(httpUrl("/api/interfaces"), InterfaceStatusDto[].class).getBody();
        assertThat(afterMessage)
                .filteredOn(dto -> "rada".equals(dto.key()))
                .allMatch(dto -> dto.receivedCount() >= 1);
    }

    @Test
    void stoppingInterface_reportsNotListening() {
        restTemplate.postForEntity(httpUrl("/api/interfaces/rada/start"), null, InterfaceStatusDto[].class);
        restTemplate.postForEntity(httpUrl("/api/interfaces/rada/stop"), null, InterfaceStatusDto[].class);

        InterfaceStatusDto[] statuses =
                restTemplate.getForEntity(httpUrl("/api/interfaces"), InterfaceStatusDto[].class).getBody();

        assertThat(statuses)
                .filteredOn(dto -> "rada".equals(dto.key()))
                .allMatch(dto -> !dto.listening());
    }

    private static byte[] radaStatusPayload() {
        RadaHeader header = new RadaHeader();
        header.setMsgCounter(1);
        header.setMsgType(RADA_STATUS_OPCODE);

        RadaStatus status = new RadaStatus();
        status.setHeader(header);
        status.setRadarSoftwareVersion(7);
        status.setRecordingState(1);
        status.setWorkingMode(2);

        return ReflectiveStructCodec.encode(status);
    }
}
