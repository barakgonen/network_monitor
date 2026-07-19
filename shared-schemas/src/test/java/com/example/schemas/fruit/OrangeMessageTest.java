package com.example.schemas.fruit;

import com.example.schemacore.ReflectiveMessageDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrangeMessageTest {

    @ParameterizedTest
    @EnumSource(FruitFreshness.class)
    void toByteArray_thenFromByteBuffer_roundTripsSourceFarmAndFreshness(FruitFreshness freshness) {
        OrangeMessage message = new OrangeMessage("Sunny Acres", freshness);

        OrangeMessage decoded = OrangeMessage.fromByteBuffer(ByteBuffer.wrap(message.toByteArray()));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void roundTrips_emptySourceFarm() {
        OrangeMessage message = new OrangeMessage("", FruitFreshness.VERY_FRESH);

        OrangeMessage decoded = OrangeMessage.fromByteBuffer(ByteBuffer.wrap(message.toByteArray()));

        assertThat(decoded.sourceFarm()).isEqualTo("");
    }

    @Test
    void roundTrips_unicodeSourceFarm() {
        OrangeMessage message = new OrangeMessage("果园 ❤", FruitFreshness.UNKNOWN);

        OrangeMessage decoded = OrangeMessage.fromByteBuffer(ByteBuffer.wrap(message.toByteArray()));

        assertThat(decoded.sourceFarm()).isEqualTo("果园 ❤");
    }

    @Test
    void fromByteBuffer_whenTooShort_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> OrangeMessage.fromByteBuffer(ByteBuffer.allocate(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void toByteArray_exactByteLayout() {
        byte[] body = new OrangeMessage("ab", FruitFreshness.NOT_FRESH).toByteArray();

        ByteBuffer buffer = ByteBuffer.wrap(body);
        assertThat(buffer.getInt()).isEqualTo(2);
        byte[] farmBytes = new byte[2];
        buffer.get(farmBytes);
        assertThat(new String(farmBytes)).isEqualTo("ab");
        assertThat(buffer.get()).isEqualTo(FruitFreshness.NOT_FRESH.getCode());
    }

    @Test
    void reflectiveMessageDefinition_decodeBody_usesWireNameForFreshness() throws Exception {
        ReflectiveMessageDefinition definition =
                new ReflectiveMessageDefinition("Fruit Interface", "Orange", 1001, OrangeMessage.class);

        byte[] body = new OrangeMessage("Sunny Acres", FruitFreshness.VERY_FRESH).toByteArray();

        Map<String, Object> fields = definition.decodeBody(ByteBuffer.wrap(body));

        assertThat(fields.get("sourceFarm")).isEqualTo("Sunny Acres");
        assertThat(fields.get("freshness")).isEqualTo("very_fresh");
    }

    @Test
    void reflectiveMessageDefinition_encodeBody_fromWireNameFieldMap() throws Exception {
        ReflectiveMessageDefinition definition =
                new ReflectiveMessageDefinition("Fruit Interface", "Orange", 1001, OrangeMessage.class);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sourceFarm", "Green Valley");
        fields.put("freshness", "not_fresh");

        byte[] body = definition.encodeBody(fields);
        OrangeMessage decoded = (OrangeMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new OrangeMessage("Green Valley", FruitFreshness.NOT_FRESH));
    }
}
