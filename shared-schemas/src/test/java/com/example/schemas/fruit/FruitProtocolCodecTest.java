package com.example.schemas.fruit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FruitProtocolCodecTest {

    private final FruitProtocolCodec codec = new FruitProtocolCodec();

    @ParameterizedTest
    @EnumSource(FruitFreshness.class)
    void encodeOrange_thenDecode_roundTripsSourceFarmAndFreshness(FruitFreshness freshness) {
        OrangeMessage message = new OrangeMessage("Sunny Acres", freshness);
        byte[] payload = codec.encodeOrange(message, 1_000L).payload();

        FruitProtocolCodec.DecodedFruitMessage decoded = codec.decode(payload);

        assertThat(decoded.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(decoded.messageType()).isEqualTo("Orange");
        assertThat(decoded.bodyFields().get("sourceFarm")).isEqualTo("Sunny Acres");
        assertThat(decoded.bodyFields().get("freshness")).isEqualTo(freshness.getWireName());
    }

    @Test
    void encodeOrange_withEmptySourceFarm_roundTrips() {
        OrangeMessage message = new OrangeMessage("", FruitFreshness.VERY_FRESH);
        byte[] payload = codec.encodeOrange(message, 1L).payload();

        FruitProtocolCodec.DecodedFruitMessage decoded = codec.decode(payload);

        assertThat(decoded.bodyFields().get("sourceFarm")).isEqualTo("");
    }

    @Test
    void encodeOrange_withUnicodeSourceFarm_roundTripsCorrectly() {
        OrangeMessage message = new OrangeMessage("果园 ❤", FruitFreshness.UNKNOWN);
        byte[] payload = codec.encodeOrange(message, 1L).payload();

        FruitProtocolCodec.DecodedFruitMessage decoded = codec.decode(payload);

        assertThat(decoded.bodyFields().get("sourceFarm")).isEqualTo("果园 ❤");
    }

    @Test
    void encodeBanana_thenDecode_roundTripsColorAndWeight() {
        BananaMessage message = new BananaMessage("yellow", 123.456);
        byte[] payload = codec.encodeBanana(message, 1L).payload();

        FruitProtocolCodec.DecodedFruitMessage decoded = codec.decode(payload);

        assertThat(decoded.messageType()).isEqualTo("Banana");
        assertThat(decoded.bodyFields().get("color")).isEqualTo("yellow");
        assertThat(decoded.bodyFields().get("weight")).isEqualTo(123.456);
    }

    @Test
    void encodeBanana_withEdgeWeights_roundTrips() {
        for (double weight : new double[] {0.0, -5.5, Double.MAX_VALUE}) {
            BananaMessage message = new BananaMessage("green", weight);
            byte[] payload = codec.encodeBanana(message, 1L).payload();

            FruitProtocolCodec.DecodedFruitMessage decoded = codec.decode(payload);

            assertThat(decoded.bodyFields().get("weight")).isEqualTo(weight);
        }
    }

    @Test
    void encodeBanana_withNaNWeight_roundTripsBitForBit() {
        BananaMessage message = new BananaMessage("brown", Double.NaN);
        byte[] payload = codec.encodeBanana(message, 1L).payload();

        FruitProtocolCodec.DecodedFruitMessage decoded = codec.decode(payload);

        double decodedWeight = (double) decoded.bodyFields().get("weight");
        assertThat(Double.doubleToLongBits(decodedWeight)).isEqualTo(Double.doubleToLongBits(Double.NaN));
    }

    @Test
    void decode_withUnknownOpcode_setsMessageTypeUnknownAndLeavesBodyFieldsEmpty() {
        byte[] payload = com.example.schemacore.ProtocolHeaderCodec.encodeMessage(9999, 1L, new byte[0]);

        FruitProtocolCodec.DecodedFruitMessage decoded = codec.decode(payload);

        assertThat(decoded.messageType()).isEqualTo("Unknown");
        assertThat(decoded.bodyFields()).isEmpty();
    }

    @Test
    void decodeOrangeBody_whenBodyTooShort_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        assertThatThrownBy(() -> FruitProtocolCodec.decodeOrangeBody(buffer, new java.util.LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void decodeOrangeBody_whenSourceFarmLengthNegative_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.putInt(-1);
        buffer.put((byte) 1);
        buffer.flip();

        assertThatThrownBy(() -> FruitProtocolCodec.decodeOrangeBody(buffer, new java.util.LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sourceFarm length");
    }

    @Test
    void decodeOrangeBody_whenSourceFarmLengthOverrunsBuffer_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.putInt(100);
        buffer.put((byte) 1);
        buffer.flip();

        assertThatThrownBy(() -> FruitProtocolCodec.decodeOrangeBody(buffer, new java.util.LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sourceFarm length");
    }

    @Test
    void decodeOrangeBody_whenFreshnessByteUnrecognized_decodesAsUnknown() {
        byte[] farmBytes = "farm".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + farmBytes.length + Byte.BYTES);
        buffer.putInt(farmBytes.length);
        buffer.put(farmBytes);
        buffer.put((byte) 99);
        buffer.flip();

        java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
        FruitProtocolCodec.decodeOrangeBody(buffer, fields);

        assertThat(fields.get("freshness")).isEqualTo(FruitFreshness.UNKNOWN.getWireName());
    }

    @Test
    void decodeBananaBody_whenBodyTooShort_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        assertThatThrownBy(() -> FruitProtocolCodec.decodeBananaBody(buffer, new java.util.LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void decodeBananaBody_whenColorLengthNegative_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Double.BYTES);
        buffer.putInt(-1);
        buffer.putDouble(1.0);
        buffer.flip();

        assertThatThrownBy(() -> FruitProtocolCodec.decodeBananaBody(buffer, new java.util.LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid color length");
    }

    @Test
    void decodeBananaBody_whenColorLengthOverrunsBuffer_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Double.BYTES);
        buffer.putInt(100);
        buffer.putDouble(1.0);
        buffer.flip();

        assertThatThrownBy(() -> FruitProtocolCodec.decodeBananaBody(buffer, new java.util.LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid color length");
    }

    @Test
    void encodeOrangeBody_exactByteLayout() {
        OrangeMessage message = new OrangeMessage("ab", FruitFreshness.NOT_FRESH);
        byte[] body = FruitProtocolCodec.encodeOrangeBody(message);

        ByteBuffer buffer = ByteBuffer.wrap(body);
        assertThat(buffer.getInt()).isEqualTo(2);
        byte[] farmBytes = new byte[2];
        buffer.get(farmBytes);
        assertThat(new String(farmBytes, StandardCharsets.UTF_8)).isEqualTo("ab");
        assertThat(buffer.get()).isEqualTo(FruitFreshness.NOT_FRESH.getCode());
    }

    @Test
    void encodeBananaBody_exactByteLayout() {
        BananaMessage message = new BananaMessage("cd", 42.5);
        byte[] body = FruitProtocolCodec.encodeBananaBody(message);

        ByteBuffer buffer = ByteBuffer.wrap(body);
        assertThat(buffer.getInt()).isEqualTo(2);
        byte[] colorBytes = new byte[2];
        buffer.get(colorBytes);
        assertThat(new String(colorBytes, StandardCharsets.UTF_8)).isEqualTo("cd");
        assertThat(buffer.getDouble()).isEqualTo(42.5);
    }
}
