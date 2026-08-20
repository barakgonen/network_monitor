package com.example.tester.payload;

import com.example.schemacore.envelope.ProtocolHeader;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemas.candy.CandyMessage;
import com.example.schemas.fruit.BananaMessage;
import com.example.schemas.fruit.OrangeMessage;
import com.example.schemas.ping.PingMessage;
import com.example.schemas.rada.messages.RadaExtendedStatus;
import com.example.schemas.rada.messages.RadaExtendedStatusMrs;
import com.example.schemas.rada.messages.RadaStatus;
import com.example.schemas.rada.messages.RadaTracksExtended;
import com.example.schemas.weather.TemperatureReadingMessage;
import com.example.tester.config.CandyPayloadConfig;
import com.example.tester.config.FruitPayloadConfig;
import com.example.tester.config.PayloadConfig;
import com.example.tester.config.PayloadMode;
import com.example.tester.config.PingPayloadConfig;
import com.example.tester.config.WeatherPayloadConfig;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

        OrangeMessage decoded = (OrangeMessage) decodeBody(payload, 1001, OrangeMessage.class);
        assertThat(decoded.sourceFarm()).isEqualTo("farm-x");
        assertThat(decoded.freshness().getWireName()).isEqualTo("very_fresh");
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

        BananaMessage decoded = (BananaMessage) decodeBody(payload, 1002, BananaMessage.class);
        assertThat(decoded.color()).isEqualTo("green");
        assertThat(decoded.weight()).isEqualTo(42.0);
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

        TemperatureReadingMessage decoded =
                (TemperatureReadingMessage) decodeBody(payload, 2001, TemperatureReadingMessage.class);
        assertThat(decoded.stationId()).isEqualTo("station-z");
        assertThat(decoded.condition().getWireName()).isEqualTo("rainy");
    }

    @Test
    void create_withPingMode_producesDecodablePingPayload() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.PING);
        PingPayloadConfig ping = new PingPayloadConfig();
        ping.setSequence(77);
        config.setPing(ping);

        byte[] payload = factory.create(config);

        PingMessage decoded = (PingMessage) decodeBody(payload, 3001, PingMessage.class);
        assertThat(decoded.sequence()).isEqualTo(77);
    }

    @Test
    void create_withCandyMode_producesDecodableCandyPayload() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.CANDY);
        CandyPayloadConfig candy = new CandyPayloadConfig();
        candy.setName("chocolate-bar");
        candy.setCalories(250.5);
        config.setCandy(candy);

        byte[] payload = factory.create(config);

        CandyMessage decoded = (CandyMessage) decodeBody(payload, 4001, CandyMessage.class);
        assertThat(decoded.name()).isEqualTo("chocolate-bar");
        assertThat(decoded.calories()).isEqualTo(250.5);
    }

    @Test
    void create_withRadaStatusMode_producesDecodableRandomizedPayloadWithFixedOpcode() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.RADA_STATUS);

        byte[] payload = factory.create(config);

        // Rada messages embed their own header, so decode directly rather than via the shared
        // fixed-envelope ProtocolHeaderCodec used by the legacy message types above.
        RadaStatus decoded = ReflectiveStructCodec.decode(RadaStatus.class, payload);
        assertThat(decoded.getHeader().getMsgType()).isEqualTo(3);
    }

    @Test
    void create_withRadaExtendedStatusMode_producesDecodableRandomizedPayloadWithFixedOpcode() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.RADA_EXTENDED_STATUS);

        byte[] payload = factory.create(config);

        RadaExtendedStatus decoded = ReflectiveStructCodec.decode(RadaExtendedStatus.class, payload);
        assertThat(decoded.getHeader().getMsgType()).isEqualTo(1);
    }

    @Test
    void create_withRadaExtendedStatusLittleEndianMode_producesLittleEndianDecodablePayload() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.RADA_EXTENDED_STATUS_LITTLE_ENDIAN);

        byte[] payload = factory.create(config);

        RadaExtendedStatus decoded = ReflectiveStructCodec.decode(RadaExtendedStatus.class, payload, ByteOrder.LITTLE_ENDIAN);
        assertThat(decoded.getHeader().getMsgType()).isEqualTo(1);

        // Sanity check the payload really is little-endian, not just decodable regardless of
        // order: msgType is a non-palindromic 4-byte int (00 00 00 01), so misreading it
        // big-endian must NOT resolve to opcode 1.
        RadaExtendedStatus misreadAsBigEndian = ReflectiveStructCodec.decode(RadaExtendedStatus.class, payload, ByteOrder.BIG_ENDIAN);
        assertThat(misreadAsBigEndian.getHeader().getMsgType()).isNotEqualTo(1);
    }

    @Test
    void create_withRadaExtendedStatusMrsMode_producesDecodableRandomizedPayloadWithFixedOpcode() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.RADA_EXTENDED_STATUS_MRS);

        byte[] payload = factory.create(config);

        RadaExtendedStatusMrs decoded = ReflectiveStructCodec.decode(RadaExtendedStatusMrs.class, payload);
        assertThat(decoded.getHeader().getMsgType()).isEqualTo(2);
    }

    @Test
    void create_withRadaTracksExtendedMode_producesDecodablePayloadWithFixedSizeArrays() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.RADA_TRACKS_EXTENDED);

        byte[] payload = factory.create(config);

        RadaTracksExtended decoded = ReflectiveStructCodec.decode(RadaTracksExtended.class, payload);
        assertThat(decoded.getHeader().getMsgType()).isEqualTo(4);
        assertThat(decoded.getTrackData()).hasSize(10);
        assertThat(decoded.getPlotData()).hasSize(10);
    }

    @Test
    void create_withPetsCreateMode_throwsUnsupportedOperationException() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.PETS_CREATE);

        // REST modes are dispatched via RestPublisher in TesterMain before PayloadFactory.create()
        // is ever called - this documents that PayloadFactory itself refuses to encode them as
        // wire-format bytes, rather than silently doing something wrong if that dispatch is ever
        // accidentally bypassed.
        assertThatThrownBy(() -> factory.create(config))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("RestPublisher");
    }

    @Test
    void create_withPetsGetMode_throwsUnsupportedOperationException() {
        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.PETS_GET);

        assertThatThrownBy(() -> factory.create(config))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("RestPublisher");
    }

    private Object decodeBody(byte[] payload, int expectedOpcode, Class<?> messageClass) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);
        assertThat(header.opcode()).isEqualTo(expectedOpcode);

        byte[] body = new byte[buffer.remaining()];
        buffer.get(body);

        return ReflectiveStructCodec.decode(messageClass, body);
    }
}
