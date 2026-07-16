package com.example.schemas.weather;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemperatureReadingMessageDefinitionTest {

    private final TemperatureReadingMessageDefinition definition = new TemperatureReadingMessageDefinition();

    @Test
    void interfaceName_and_messageType_and_opcode_and_messageClass_returnExpectedConstants() {
        assertThat(definition.interfaceName()).isEqualTo("Weather Interface");
        assertThat(definition.messageType()).isEqualTo("TemperatureReading");
        assertThat(definition.opcode()).isEqualTo(WeatherOpcodes.TEMPERATURE_READING);
        assertThat(definition.messageClass()).isEqualTo(TemperatureReadingMessage.class);
    }

    @Test
    void decodeMessage_returnsTypedTemperatureReadingMessage() throws Exception {
        TemperatureReadingMessage original = new TemperatureReadingMessage("station-a", 18.2, WeatherCondition.CLOUDY);
        byte[] body = WeatherProtocolCodec.encodeTemperatureReadingBody(original);

        TemperatureReadingMessage decoded = (TemperatureReadingMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void encodeBody_fromFieldsMap_roundTripsWithDecodeMessage() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("stationId", "station-b");
        fields.put("temperatureCelsius", 30.0);
        fields.put("condition", "sunny");

        byte[] body = definition.encodeBody(fields);
        TemperatureReadingMessage decoded = (TemperatureReadingMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new TemperatureReadingMessage("station-b", 30.0, WeatherCondition.SUNNY));
    }

    @Test
    void encodeBody_fromProtocolMessageInstance_roundTripsWithDecodeMessage() throws Exception {
        TemperatureReadingMessage message = new TemperatureReadingMessage("station-c", -5.0, WeatherCondition.RAINY);

        byte[] body = definition.encodeBody((com.example.schemacore.ProtocolMessage) message);
        TemperatureReadingMessage decoded = (TemperatureReadingMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void encodeBody_withMissingStationIdField_throwsIllegalArgumentException() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("temperatureCelsius", 1.0);
        fields.put("condition", "sunny");

        assertThatThrownBy(() -> definition.encodeBody(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeBody_withMissingTemperatureField_throwsIllegalArgumentException() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("stationId", "s1");
        fields.put("condition", "sunny");

        assertThatThrownBy(() -> definition.encodeBody(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeBody_withMissingConditionField_throwsIllegalArgumentException() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("stationId", "s1");
        fields.put("temperatureCelsius", 1.0);

        assertThatThrownBy(() -> definition.encodeBody(fields))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
