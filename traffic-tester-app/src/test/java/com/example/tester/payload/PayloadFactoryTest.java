package com.example.tester.payload;

import com.example.schemas.fruit.FruitProtocolCodec;
import com.example.schemas.ping.PingProtocolCodec;
import com.example.schemas.weather.WeatherProtocolCodec;
import com.example.tester.config.FruitPayloadConfig;
import com.example.tester.config.PayloadConfig;
import com.example.tester.config.PayloadMode;
import com.example.tester.config.PingPayloadConfig;
import com.example.tester.config.WeatherPayloadConfig;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadFactoryTest {

    private final PayloadFactory factory = new PayloadFactory();

    @Test
    void create_withTextMode_encodesUtf8Bytes() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.TEXT);
        config.setText("hello");

        assertThat(factory.create(config)).isEqualTo("hello".getBytes());
    }

    @Test
    void create_withBase64Mode_decodesBase64() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.BASE64);
        config.setBase64(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));

        assertThat(factory.create(config)).isEqualTo(new byte[] {1, 2, 3});
    }

    @Test
    void create_withHexMode_decodesHexPairs() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.HEX);
        config.setHex("01 02\nff");

        assertThat(factory.create(config)).isEqualTo(new byte[] {0x01, 0x02, (byte) 0xff});
    }

    @Test
    void create_withHexModeOddLength_throwsIllegalArgumentException() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.HEX);
        config.setHex("abc");

        assertThatThrownBy(() -> factory.create(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("even number");
    }

    @Test
    void create_withFruitOrangeMode_producesDecodableOrangePayload() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.FRUIT_ORANGE);
        FruitPayloadConfig fruit = new FruitPayloadConfig();
        fruit.setSourceFarm("farm-x");
        fruit.setFreshness("very_fresh");
        config.setFruit(fruit);

        byte[] payload = factory.create(config);

        FruitProtocolCodec.DecodedFruitMessage decoded = new FruitProtocolCodec().decode(payload);
        assertThat(decoded.messageType()).isEqualTo("Orange");
        assertThat(decoded.bodyFields().get("sourceFarm")).isEqualTo("farm-x");
        assertThat(decoded.bodyFields().get("freshness")).isEqualTo("very_fresh");
    }

    @Test
    void create_withFruitBananaMode_producesDecodableBananaPayload() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.FRUIT_BANANA);
        FruitPayloadConfig fruit = new FruitPayloadConfig();
        fruit.setColor("green");
        fruit.setWeight(42.0);
        config.setFruit(fruit);

        byte[] payload = factory.create(config);

        FruitProtocolCodec.DecodedFruitMessage decoded = new FruitProtocolCodec().decode(payload);
        assertThat(decoded.messageType()).isEqualTo("Banana");
        assertThat(decoded.bodyFields().get("color")).isEqualTo("green");
        assertThat(decoded.bodyFields().get("weight")).isEqualTo(42.0);
    }

    @Test
    void create_withWeatherTemperatureReadingMode_producesDecodablePayload() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.WEATHER_TEMPERATURE_READING);
        WeatherPayloadConfig weather = new WeatherPayloadConfig();
        weather.setStationId("station-z");
        weather.setTemperatureCelsius(10.0);
        weather.setCondition("rainy");
        config.setWeather(weather);

        byte[] payload = factory.create(config);

        WeatherProtocolCodec.DecodedWeatherMessage decoded = new WeatherProtocolCodec().decode(payload);
        assertThat(decoded.messageType()).isEqualTo("TemperatureReading");
        assertThat(decoded.bodyFields().get("stationId")).isEqualTo("station-z");
        assertThat(decoded.bodyFields().get("condition")).isEqualTo("rainy");
    }

    @Test
    void create_withPingMode_producesDecodablePingPayload() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.PING);
        PingPayloadConfig ping = new PingPayloadConfig();
        ping.setSequence(77);
        config.setPing(ping);

        byte[] payload = factory.create(config);

        PingProtocolCodec.DecodedPingMessage decoded = new PingProtocolCodec().decode(payload);
        assertThat(decoded.messageType()).isEqualTo("Ping");
        assertThat(decoded.bodyFields().get("sequence")).isEqualTo(77);
    }
}
