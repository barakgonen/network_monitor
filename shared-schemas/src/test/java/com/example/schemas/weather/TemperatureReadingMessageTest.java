package com.example.schemas.weather;

import com.example.schemacore.ReflectiveMessageDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemperatureReadingMessageTest {

    @ParameterizedTest
    @EnumSource(WeatherCondition.class)
    void toByteArray_thenFromByteBuffer_roundTripsAllFieldsForEachCondition(WeatherCondition condition) {
        TemperatureReadingMessage message = new TemperatureReadingMessage("station-1", 21.5, condition);

        TemperatureReadingMessage decoded = TemperatureReadingMessage.fromByteBuffer(ByteBuffer.wrap(message.toByteArray()));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void roundTrips_negativeTemperature() {
        TemperatureReadingMessage message = new TemperatureReadingMessage("station-2", -40.0, WeatherCondition.CLOUDY);

        TemperatureReadingMessage decoded = TemperatureReadingMessage.fromByteBuffer(ByteBuffer.wrap(message.toByteArray()));

        assertThat(decoded.temperatureCelsius()).isEqualTo(-40.0);
    }

    @Test
    void roundTrips_unicodeStationId() {
        TemperatureReadingMessage message = new TemperatureReadingMessage("站-Ω", 10.0, WeatherCondition.SUNNY);

        TemperatureReadingMessage decoded = TemperatureReadingMessage.fromByteBuffer(ByteBuffer.wrap(message.toByteArray()));

        assertThat(decoded.stationId()).isEqualTo("站-Ω");
    }

    @Test
    void fromByteBuffer_whenTooShort_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> TemperatureReadingMessage.fromByteBuffer(ByteBuffer.allocate(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void fromByteBuffer_whenStationIdLengthNegative_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Double.BYTES + Byte.BYTES);
        buffer.putInt(-1);
        buffer.putDouble(1.0);
        buffer.put((byte) 1);
        buffer.flip();

        assertThatThrownBy(() -> TemperatureReadingMessage.fromByteBuffer(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid stationId length");
    }

    @Test
    void fromByteBuffer_whenStationIdLengthOverrunsBuffer_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Double.BYTES + Byte.BYTES);
        buffer.putInt(100);
        buffer.putDouble(1.0);
        buffer.put((byte) 1);
        buffer.flip();

        assertThatThrownBy(() -> TemperatureReadingMessage.fromByteBuffer(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid stationId length");
    }

    @Test
    void fromByteBuffer_whenConditionByteUnrecognized_decodesAsUnknown() {
        byte[] stationBytes = "s1".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + stationBytes.length + Double.BYTES + Byte.BYTES);
        buffer.putInt(stationBytes.length);
        buffer.put(stationBytes);
        buffer.putDouble(5.0);
        buffer.put((byte) 99);
        buffer.flip();

        TemperatureReadingMessage decoded = TemperatureReadingMessage.fromByteBuffer(buffer);

        assertThat(decoded.condition()).isEqualTo(WeatherCondition.UNKNOWN);
    }

    @Test
    void toByteArray_exactByteLayout() {
        byte[] body = new TemperatureReadingMessage("ab", 12.5, WeatherCondition.RAINY).toByteArray();

        ByteBuffer buffer = ByteBuffer.wrap(body);
        assertThat(buffer.getInt()).isEqualTo(2);
        byte[] stationBytes = new byte[2];
        buffer.get(stationBytes);
        assertThat(new String(stationBytes, StandardCharsets.UTF_8)).isEqualTo("ab");
        assertThat(buffer.getDouble()).isEqualTo(12.5);
        assertThat(buffer.get()).isEqualTo(WeatherCondition.RAINY.getCode());
    }

    @Test
    void reflectiveMessageDefinition_decodeBody_usesWireNameForCondition() throws Exception {
        ReflectiveMessageDefinition definition = new ReflectiveMessageDefinition(
                "Weather Interface", "TemperatureReading", 2001, TemperatureReadingMessage.class);

        byte[] body = new TemperatureReadingMessage("station-1", 21.5, WeatherCondition.RAINY).toByteArray();

        Map<String, Object> fields = definition.decodeBody(ByteBuffer.wrap(body));

        assertThat(fields.get("stationId")).isEqualTo("station-1");
        assertThat(fields.get("temperatureCelsius")).isEqualTo(21.5);
        assertThat(fields.get("condition")).isEqualTo("rainy");
    }

    @Test
    void reflectiveMessageDefinition_encodeBody_fromWireNameFieldMap() throws Exception {
        ReflectiveMessageDefinition definition = new ReflectiveMessageDefinition(
                "Weather Interface", "TemperatureReading", 2001, TemperatureReadingMessage.class);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("stationId", "station-3");
        fields.put("temperatureCelsius", 5.0);
        fields.put("condition", "sunny");

        byte[] body = definition.encodeBody(fields);
        TemperatureReadingMessage decoded =
                (TemperatureReadingMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new TemperatureReadingMessage("station-3", 5.0, WeatherCondition.SUNNY));
    }
}
