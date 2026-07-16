package com.example.schemas.weather;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeatherProtocolCodecTest {

    private final WeatherProtocolCodec codec = new WeatherProtocolCodec();

    @ParameterizedTest
    @EnumSource(WeatherCondition.class)
    void encodeTemperatureReading_thenDecode_roundTripsAllFieldsForEachCondition(WeatherCondition condition) {
        TemperatureReadingMessage message = new TemperatureReadingMessage("station-1", 21.5, condition);
        byte[] payload = codec.encodeTemperatureReading(message, 1L).payload();

        WeatherProtocolCodec.DecodedWeatherMessage decoded = codec.decode(payload);

        assertThat(decoded.interfaceName()).isEqualTo("Weather Interface");
        assertThat(decoded.messageType()).isEqualTo("TemperatureReading");
        assertThat(decoded.bodyFields().get("stationId")).isEqualTo("station-1");
        assertThat(decoded.bodyFields().get("temperatureCelsius")).isEqualTo(21.5);
        assertThat(decoded.bodyFields().get("condition")).isEqualTo(condition.getWireName());
    }

    @Test
    void encodeTemperatureReading_withNegativeTemperature_roundTrips() {
        TemperatureReadingMessage message = new TemperatureReadingMessage("station-2", -40.0, WeatherCondition.CLOUDY);
        byte[] payload = codec.encodeTemperatureReading(message, 1L).payload();

        WeatherProtocolCodec.DecodedWeatherMessage decoded = codec.decode(payload);

        assertThat(decoded.bodyFields().get("temperatureCelsius")).isEqualTo(-40.0);
    }

    @Test
    void encodeTemperatureReading_withUnicodeStationId_roundTrips() {
        TemperatureReadingMessage message = new TemperatureReadingMessage("站-Ω", 10.0, WeatherCondition.SUNNY);
        byte[] payload = codec.encodeTemperatureReading(message, 1L).payload();

        WeatherProtocolCodec.DecodedWeatherMessage decoded = codec.decode(payload);

        assertThat(decoded.bodyFields().get("stationId")).isEqualTo("站-Ω");
    }

    @Test
    void decode_withUnknownOpcode_setsMessageTypeUnknown() {
        byte[] payload = com.example.schemacore.ProtocolHeaderCodec.encodeMessage(1, 1L, new byte[0]);

        WeatherProtocolCodec.DecodedWeatherMessage decoded = codec.decode(payload);

        assertThat(decoded.messageType()).isEqualTo("Unknown");
        assertThat(decoded.bodyFields()).isEmpty();
    }

    @Test
    void decodeTemperatureReadingBody_whenTooShort_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        assertThatThrownBy(() -> WeatherProtocolCodec.decodeTemperatureReadingBody(buffer, new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void decodeTemperatureReadingBody_whenStationIdLengthNegative_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Double.BYTES + Byte.BYTES);
        buffer.putInt(-1);
        buffer.putDouble(1.0);
        buffer.put((byte) 1);
        buffer.flip();

        assertThatThrownBy(() -> WeatherProtocolCodec.decodeTemperatureReadingBody(buffer, new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid stationId length");
    }

    @Test
    void decodeTemperatureReadingBody_whenStationIdLengthOverrunsBuffer_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Double.BYTES + Byte.BYTES);
        buffer.putInt(100);
        buffer.putDouble(1.0);
        buffer.put((byte) 1);
        buffer.flip();

        assertThatThrownBy(() -> WeatherProtocolCodec.decodeTemperatureReadingBody(buffer, new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid stationId length");
    }

    @Test
    void decodeTemperatureReadingBody_whenConditionByteUnrecognized_decodesAsUnknown() {
        byte[] stationBytes = "s1".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + stationBytes.length + Double.BYTES + Byte.BYTES);
        buffer.putInt(stationBytes.length);
        buffer.put(stationBytes);
        buffer.putDouble(5.0);
        buffer.put((byte) 99);
        buffer.flip();

        Map<String, Object> fields = new LinkedHashMap<>();
        WeatherProtocolCodec.decodeTemperatureReadingBody(buffer, fields);

        assertThat(fields.get("condition")).isEqualTo(WeatherCondition.UNKNOWN.getWireName());
    }

    @Test
    void encodeTemperatureReadingBody_exactByteLayout() {
        TemperatureReadingMessage message = new TemperatureReadingMessage("ab", 12.5, WeatherCondition.RAINY);
        byte[] body = WeatherProtocolCodec.encodeTemperatureReadingBody(message);

        ByteBuffer buffer = ByteBuffer.wrap(body);
        assertThat(buffer.getInt()).isEqualTo(2);
        byte[] stationBytes = new byte[2];
        buffer.get(stationBytes);
        assertThat(new String(stationBytes, StandardCharsets.UTF_8)).isEqualTo("ab");
        assertThat(buffer.getDouble()).isEqualTo(12.5);
        assertThat(buffer.get()).isEqualTo(WeatherCondition.RAINY.getCode());
    }
}
