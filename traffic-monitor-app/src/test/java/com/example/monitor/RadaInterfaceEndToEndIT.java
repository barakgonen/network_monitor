package com.example.monitor;

import com.example.monitor.interfaces.InterfaceStatusDto;
import com.example.monitor.model.ObservedMessage;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemas.rada.messages.RadaExtendedStatus;
import com.example.schemas.rada.messages.RadaStatus;
import com.example.schemas.rada.struct.RadaHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class RadaInterfaceEndToEndIT extends AbstractIntegrationTestBase {

    private static final int RADA_STATUS_OPCODE = 3;
    private static final int RADA_EXTENDED_STATUS_OPCODE = 1;

    @AfterEach
    void stopRadaInterfaces() {
        restTemplate.postForEntity(httpUrl("/api/interfaces/rada/stop"), null, InterfaceStatusDto[].class);
        restTemplate.postForEntity(httpUrl("/api/interfaces/rada-le/stop"), null, InterfaceStatusDto[].class);
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

        sendUdp(radaPort, radaStatusPayload());

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

    @Test
    void twoRadaInterfaces_decodeSameMessageType_usingIndependentlyConfiguredByteOrder() throws Exception {
        restTemplate.postForEntity(httpUrl("/api/interfaces/rada/start"), null, InterfaceStatusDto[].class);
        restTemplate.postForEntity(httpUrl("/api/interfaces/rada-le/start"), null, InterfaceStatusDto[].class);

        RadaExtendedStatus message = radaExtendedStatusMessage();
        byte[] bigEndianBytes = ReflectiveStructCodec.encode(message, ByteOrder.BIG_ENDIAN);
        byte[] littleEndianBytes = ReflectiveStructCodec.encode(message, ByteOrder.LITTLE_ENDIAN);
        // Sanity check the two encodings of the identical logical message really are different
        // bytes on the wire - otherwise the rest of this test wouldn't prove anything.
        assertThat(littleEndianBytes).isNotEqualTo(bigEndianBytes);

        // "rada" has no message-level override, so it inherits the interface's BIG_ENDIAN default.
        sendUdp(radaPort, bigEndianBytes);
        // "rada-le" overrides RadaExtendedStatus specifically to LITTLE_ENDIAN.
        sendUdp(radaLePort, littleEndianBytes);

        ObservedMessage bigEndianDecoded = awaitStoreContains(
                m -> "Rada Interface".equals(m.interfaceName()) && "RadaExtendedStatus".equals(m.messageType()));
        ObservedMessage littleEndianDecoded = awaitStoreContains(m ->
                "Rada Interface (Little Endian Extended Status)".equals(m.interfaceName())
                        && "RadaExtendedStatus".equals(m.messageType()));

        assertThat(bigEndianDecoded.parseError()).isNull();
        assertThat(littleEndianDecoded.parseError()).isNull();
        assertThat((Double) bigEndianDecoded.body().get("latitude")).isEqualTo(message.getLatitude(), offset(1e-9));
        assertThat((Double) littleEndianDecoded.body().get("latitude")).isEqualTo(message.getLatitude(), offset(1e-9));
    }

    private static RadaExtendedStatus radaExtendedStatusMessage() {
        RadaHeader header = new RadaHeader();
        header.setMsgCounter(1);
        header.setMsgType(RADA_EXTENDED_STATUS_OPCODE);

        RadaExtendedStatus message = new RadaExtendedStatus();
        message.setHeader(header);
        message.setLatitude(32.0853);
        message.setLongitude(34.7818);
        message.setAltitude(12.5f);

        return message;
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
