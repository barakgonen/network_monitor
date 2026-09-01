package com.example.monitor;

import com.example.monitor.interfaces.InterfaceStatusDto;
import com.example.monitor.model.ObservedMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the rada/rada-le interfaces, now decoded via the JSON-schema-driven
 * {@code com.example.binaryserdes} engine (serdes/rada.protocol.json) instead of the
 * {@code com.example.schemas.rada.*} classes that used to live in this module - those moved to
 * traffic-tester-app, so payloads here are hand-built with {@link ByteBuffer} instead (mirrors
 * {@link TestProtocolPayloads}'s approach for the legacy-envelope protocols).
 */
class RadaInterfaceEndToEndIT extends AbstractIntegrationTestBase {

    private static final int RADA_STATUS_OPCODE = 3;
    private static final int RADA_EXTENDED_STATUS_OPCODE = 1;

    /** RadaHeader: msgCounter(int32) + msgType(int32) + icdVersion/reserved1-3(4x uint8) + msgSize(int32) = 16 bytes. */
    private static void putRadaHeader(ByteBuffer buffer, int msgCounter, int msgType) {
        buffer.putInt(msgCounter);
        buffer.putInt(msgType);
        buffer.put((byte) 0); // icdVersion
        buffer.put((byte) 0); // reserved1
        buffer.put((byte) 0); // reserved2
        buffer.put((byte) 0); // reserved3
        buffer.putInt(0); // msgSize
    }

    private static byte[] radaStatusPayload() {
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 4 + 4 + 2 + 2 + 4 + 2);
        putRadaHeader(buffer, 1, RADA_STATUS_OPCODE);
        buffer.putInt(7); // radarSoftwareVersion
        buffer.putInt(1); // recordingState
        buffer.putInt(2); // workingMode
        buffer.putShort((short) 100); // statusFlags
        buffer.putShort((short) 200); // remainingRecordingSpace
        buffer.putInt(3); // bitStatus
        buffer.putShort((short) 50); // manufacturerData
        return buffer.array();
    }

    private static byte[] radaExtendedStatusPayload(ByteOrder byteOrder, double latitude) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + 8 + 8 + 4 * 7).order(byteOrder);
        putRadaHeader(buffer, 1, RADA_EXTENDED_STATUS_OPCODE);
        buffer.putDouble(latitude);
        buffer.putDouble(34.7818); // longitude
        buffer.putFloat(12.5f); // altitude
        buffer.putFloat(1.0f); // pitch
        buffer.putFloat(2.0f); // roll
        buffer.putFloat(3.0f); // heading
        buffer.putFloat(4.0f); // coverage1Sector1
        buffer.putFloat(5.0f); // coverage1Sector2
        buffer.putFloat(6.0f); // coverage1Radius
        return buffer.array();
    }

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
        assertThat(message.header().get("msgType")).isEqualTo(3);
        // Field values pass through a JSON intermediate representation (ProtocolIn/ProtocolOut),
        // so a uint32 value that fits in int range round-trips as Integer, not Long - JSON itself
        // has no int/long distinction, and Jackson's generic Map<String,Object> deserialization
        // picks the smallest Java type that fits.
        assertThat(message.body().get("radarSoftwareVersion")).isEqualTo(7);
        assertThat(message.body().get("manufacturerData")).isEqualTo(50);

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

        // "rada" has no message-level override, so it inherits the interface's BIG_ENDIAN default.
        sendUdp(radaPort, radaExtendedStatusPayload(ByteOrder.BIG_ENDIAN, 32.0853));
        // "rada-le" decodes everything LITTLE_ENDIAN instead.
        sendUdp(radaLePort, radaExtendedStatusPayload(ByteOrder.LITTLE_ENDIAN, 32.0853));

        ObservedMessage bigEndianDecoded = awaitStoreContains(
                m -> "Rada Interface".equals(m.interfaceName()) && "RadaExtendedStatus".equals(m.messageType()));
        ObservedMessage littleEndianDecoded = awaitStoreContains(m ->
                "Rada Interface (Little Endian Extended Status)".equals(m.interfaceName())
                        && "RadaExtendedStatus".equals(m.messageType()));

        assertThat(bigEndianDecoded.parseError()).isNull();
        assertThat(littleEndianDecoded.parseError()).isNull();
        assertThat((Double) bigEndianDecoded.body().get("latitude")).isEqualTo(32.0853);
        assertThat((Double) littleEndianDecoded.body().get("latitude")).isEqualTo(32.0853);
    }
}
