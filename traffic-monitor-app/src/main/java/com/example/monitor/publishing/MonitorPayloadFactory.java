package com.example.monitor.publishing;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class MonitorPayloadFactory {
//    private final FruitProtocolCodec fruitProtocolCodec = new FruitProtocolCodec();
//    private final WeatherProtocolCodec weatherProtocolCodec = new WeatherProtocolCodec();

    public byte[] create(String interfaceName, String messageType, Map<String, Object> fields) {
//        if ("Fruit Interface".equals(interfaceName) && "Orange".equals(messageType)) {
//            OrangeMessage orangeMessage = new OrangeMessage(
//                    stringValue(fields, "sourceFarm"),
//                    FruitFreshness.fromWireName(stringValue(fields, "freshness"))
//            );
//
//            return fruitProtocolCodec.encodeOrange(orangeMessage, Instant.now().toEpochMilli()).payload();
//        }
//
//        if ("Fruit Interface".equals(interfaceName) && "Banana".equals(messageType)) {
//            BananaMessage bananaMessage = new BananaMessage(
//                    stringValue(fields, "color"),
//                    doubleValue(fields, "weight")
//            );
//
//            return fruitProtocolCodec.encodeBanana(bananaMessage, Instant.now().toEpochMilli()).payload();
//        }
//
//        if ("Weather Interface".equals(interfaceName) && "TemperatureReading".equals(messageType)) {
//            TemperatureReadingMessage message = new TemperatureReadingMessage(
//                    stringValue(fields, "stationId"),
//                    doubleValue(fields, "temperatureCelsius"),
//                    WeatherCondition.fromWireName(stringValue(fields, "condition"))
//            );
//
//            return weatherProtocolCodec.encodeTemperatureReading(message, Instant.now().toEpochMilli()).payload();
//        }

        throw new IllegalArgumentException("Unsupported message: interfaceName=" + interfaceName + ", messageType=" + messageType);
    }

    private String stringValue(Map<String, Object> fields, String name) {
        Object value = fields == null ? null : fields.get(name);

        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + name);
        }

        String stringValue = String.valueOf(value);

        if (stringValue.isBlank()) {
            throw new IllegalArgumentException("Field must not be blank: " + name);
        }

        return stringValue;
    }

    private double doubleValue(Map<String, Object> fields, String name) {
        Object value = fields == null ? null : fields.get(name);

        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + name);
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field must be a valid double: " + name + ", value=" + value);
        }
    }
}
