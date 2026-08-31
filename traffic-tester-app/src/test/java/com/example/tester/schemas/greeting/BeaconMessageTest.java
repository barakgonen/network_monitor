package com.example.tester.schemas.greeting;

import com.example.schemacore.reflect.ReflectiveMessageDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BeaconMessageTest {

    @ParameterizedTest
    @CsvSource({"0,0", "32.0853,34.7818", "-90,-180", "90,180"})
    void toByteArray_thenFromByteBuffer_roundTripsLatLon(double lat, double lon) {
        BeaconMessage message = new BeaconMessage(lat, lon);
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES * 2);
        message.toByteArray(buffer);

        BeaconMessage decoded = BeaconMessage.fromByteBuffer(ByteBuffer.wrap(buffer.array()));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void toByteArray_exactByteLayout() {
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES * 2);
        new BeaconMessage(1.5, -2.5).toByteArray(buffer);

        ByteBuffer readBack = ByteBuffer.wrap(buffer.array());
        assertThat(readBack.getDouble()).isEqualTo(1.5);
        assertThat(readBack.getDouble()).isEqualTo(-2.5);
    }

    @Test
    void reflectiveMessageDefinition_decodesAndEncodesConsistently() throws Exception {
        ReflectiveMessageDefinition definition =
                new ReflectiveMessageDefinition("Greeting Interface", "Beacon", 5001, BeaconMessage.class);

        byte[] body = definition.encodeBody(Map.of("lat", 32.0853, "lon", 34.7818));
        BeaconMessage decoded = (BeaconMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new BeaconMessage(32.0853, 34.7818));
        assertThat(definition.decodeBody(ByteBuffer.wrap(body)))
                .containsEntry("lat", 32.0853)
                .containsEntry("lon", 34.7818);
    }
}
