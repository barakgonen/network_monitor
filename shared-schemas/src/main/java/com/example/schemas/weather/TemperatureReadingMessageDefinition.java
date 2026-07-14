package com.example.schemas.weather;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageFields;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class TemperatureReadingMessageDefinition implements MessageDefinition {

    @Override
    public String interfaceName() {
        return "Weather Interface";
    }

    @Override
    public String messageType() {
        return "TemperatureReading";
    }

    @Override
    public int opcode() {
        return WeatherOpcodes.TEMPERATURE_READING;
    }

    @Override
    public Map<String, Object> decodeBody(ByteBuffer body) {
        Map<String, Object> bodyFields = new LinkedHashMap<>();
        WeatherProtocolCodec.decodeTemperatureReadingBody(body, bodyFields);
        return bodyFields;
    }

    @Override
    public byte[] encodeBody(Map<String, Object> fields) {
        TemperatureReadingMessage reading = new TemperatureReadingMessage(
                MessageFields.requireString(fields, "stationId"),
                MessageFields.requireDouble(fields, "temperatureCelsius"),
                WeatherCondition.fromWireName(MessageFields.requireString(fields, "condition"))
        );

        return WeatherProtocolCodec.encodeTemperatureReadingBody(reading);
    }
}
