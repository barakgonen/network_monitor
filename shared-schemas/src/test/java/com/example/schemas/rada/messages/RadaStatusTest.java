package com.example.schemas.rada.messages;

import com.example.schemacore.ReflectiveMessageDefinition;
import com.example.schemacore.ReflectiveStructCodec;
import com.example.schemacore.StructSizeCalculator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class RadaStatusTest {

    @Test
    void toByteArray_thenFromByteArrayConstructor_roundTripsAllFields() {
        RadaStatus original = new RadaStatus();
        original.getHeader().setMsgCounter(7);
        original.getHeader().setMsgType(3);
        original.setRadarSoftwareVersion(42);
        original.setRecordingState(1);
        original.setWorkingMode(2);
        original.setStatusFlags(0xBEEF);
        original.setRemainingRecordingSpace(1234);
        original.setBitStatus(99);
        original.setManufacturerData(555);

        int size = StructSizeCalculator.calculateStructSize(RadaStatus.class);
        ByteBuffer buffer = ByteBuffer.allocate(size);
        original.toByteArray(buffer);

        RadaStatus decoded = new RadaStatus(buffer.array());

        assertThat(decoded.getHeader().getMsgCounter()).isEqualTo(7);
        assertThat(decoded.getHeader().getMsgType()).isEqualTo(3);
        assertThat(decoded.getRadarSoftwareVersion()).isEqualTo(42);
        assertThat(decoded.getStatusFlags()).isEqualTo(0xBEEF);
        assertThat(decoded.getRemainingRecordingSpace()).isEqualTo(1234);
        assertThat(decoded.getBitStatus()).isEqualTo(99);
        assertThat(decoded.getManufacturerData()).isEqualTo(555);
    }

    @Test
    void reflectiveStructCodec_encodeThenDecode_roundTrips() {
        RadaStatus original = new RadaStatus();
        original.setRadarSoftwareVersion(11);

        byte[] encoded = ReflectiveStructCodec.encode(original);
        RadaStatus decoded = ReflectiveStructCodec.decode(RadaStatus.class, encoded);

        assertThat(decoded.getRadarSoftwareVersion()).isEqualTo(11);
    }

    @Test
    void reflectiveMessageDefinition_decodeMessage_roundTrips() throws Exception {
        ReflectiveMessageDefinition definition =
                new ReflectiveMessageDefinition("Rada Interface", "RadaStatus", 3, RadaStatus.class);

        RadaStatus original = new RadaStatus();
        original.setWorkingMode(5);

        byte[] body = definition.encodeBody(original);
        RadaStatus decoded = (RadaStatus) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded.getWorkingMode()).isEqualTo(5);
    }
}
