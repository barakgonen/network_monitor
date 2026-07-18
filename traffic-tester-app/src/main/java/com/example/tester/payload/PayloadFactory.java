package com.example.tester.payload;

import com.example.schemas.candy.CandyMessage;
import com.example.schemas.candy.CandyProtocolCodec;
import com.example.schemas.fruit.BananaMessage;
import com.example.schemas.fruit.FruitFreshness;
import com.example.schemas.fruit.FruitProtocolCodec;
import com.example.schemas.fruit.OrangeMessage;
import com.example.schemas.ping.PingMessage;
import com.example.schemas.ping.PingProtocolCodec;
import com.example.schemas.weather.TemperatureReadingMessage;
import com.example.schemas.weather.WeatherCondition;
import com.example.schemas.weather.WeatherProtocolCodec;
import com.example.tester.config.PayloadConfig;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class PayloadFactory {
    private final FruitProtocolCodec fruitProtocolCodec = new FruitProtocolCodec();
    private final WeatherProtocolCodec weatherProtocolCodec = new WeatherProtocolCodec();
    private final PingProtocolCodec pingProtocolCodec = new PingProtocolCodec();
    private final CandyProtocolCodec candyProtocolCodec = new CandyProtocolCodec();

    public byte[] create(PayloadConfig config) {
        return switch (config.getMode()) {
            case TEXT -> config.getText().getBytes(StandardCharsets.UTF_8);
            case BASE64 -> Base64.getDecoder().decode(config.getBase64());
            case HEX -> parseHex(config.getHex());
            case FRUIT_ORANGE -> createFruitOrange(config);
            case FRUIT_BANANA -> createFruitBanana(config);
            case WEATHER_TEMPERATURE_READING -> createWeatherTemperatureReading(config);
            case PING -> createPing(config);
            case CANDY -> createCandy(config);
        };
    }

    private byte[] createFruitOrange(PayloadConfig config) {
        OrangeMessage orangeMessage = new OrangeMessage(
                config.getFruit().getSourceFarm(),
                FruitFreshness.fromWireName(config.getFruit().getFreshness())
        );

        return fruitProtocolCodec.encodeOrange(orangeMessage, Instant.now().toEpochMilli()).payload();
    }

    private byte[] createFruitBanana(PayloadConfig config) {
        BananaMessage bananaMessage = new BananaMessage(
                config.getFruit().getColor(),
                config.getFruit().getWeight()
        );

        return fruitProtocolCodec.encodeBanana(bananaMessage, Instant.now().toEpochMilli()).payload();
    }

    private byte[] createWeatherTemperatureReading(PayloadConfig config) {
        TemperatureReadingMessage message = new TemperatureReadingMessage(
                config.getWeather().getStationId(),
                config.getWeather().getTemperatureCelsius(),
                WeatherCondition.fromWireName(config.getWeather().getCondition())
        );

        return weatherProtocolCodec.encodeTemperatureReading(message, Instant.now().toEpochMilli()).payload();
    }

    private byte[] createPing(PayloadConfig config) {
        PingMessage pingMessage = new PingMessage(config.getPing().getSequence());

        return pingProtocolCodec.encodePing(pingMessage, Instant.now().toEpochMilli()).payload();
    }

    private byte[] createCandy(PayloadConfig config) {
        CandyMessage candyMessage = new CandyMessage(
                config.getCandy().getName(),
                config.getCandy().getCalories()
        );

        return candyProtocolCodec.encodeCandy(candyMessage, Instant.now().toEpochMilli()).payload();
    }

    private byte[] parseHex(String hex) {
        String normalized = hex.replace(" ", "").replace("\n", "").replace("\t", "");

        if (normalized.length() % 2 != 0) {
            throw new IllegalArgumentException("HEX payload must contain an even number of characters");
        }

        byte[] bytes = new byte[normalized.length() / 2];

        for (int i = 0; i < normalized.length(); i += 2) {
            String pair = normalized.substring(i, i + 2);
            bytes[i / 2] = (byte) Integer.parseInt(pair, 16);
        }

        return bytes;
    }
}
