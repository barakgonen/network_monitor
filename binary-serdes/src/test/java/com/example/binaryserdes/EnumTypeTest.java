package com.example.binaryserdes;

import com.example.binaryserdes.translators.UInt16Translator;
import com.example.binaryserdes.translators.UInt8Translator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumTypeTest {

    private static Map<String, Integer> values() {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("CELSIUS", 0);
        values.put("FAHRENHEIT", 1);
        return values;
    }

    @Test
    void roundTrips_eachDeclaredValue() {
        Type<Integer> uint8 = new Type<>("uint8", 1, Integer.class, new UInt8Translator());
        EnumType enumType = new EnumType("TemperatureUnit", uint8, values());

        assertThat(enumType.getSizeInBytes()).isEqualTo(1);

        ByteBuffer buffer = ByteBuffer.allocate(1);
        enumType.getTranslator().toBytes("FAHRENHEIT", buffer);
        buffer.flip();

        assertThat(enumType.getTranslator().fromBytes(buffer)).isEqualTo("FAHRENHEIT");
    }

    @Test
    void toBytes_withUnknownName_throws() {
        Type<Integer> uint8 = new Type<>("uint8", 1, Integer.class, new UInt8Translator());
        EnumType enumType = new EnumType("TemperatureUnit", uint8, values());

        assertThatThrownBy(() -> enumType.getTranslator().toBytes("KELVIN", ByteBuffer.allocate(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KELVIN");
    }

    @Test
    void fromBytes_withUnknownCode_throws() {
        Type<Integer> uint8 = new Type<>("uint8", 1, Integer.class, new UInt8Translator());
        EnumType enumType = new EnumType("TemperatureUnit", uint8, values());

        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte) 99);
        buffer.flip();

        assertThatThrownBy(() -> enumType.getTranslator().fromBytes(buffer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("99");
    }

    @Test
    void widerUnderlyingType_carriesEnumValueCorrectly() {
        Type<Integer> uint16 = new Type<>("uint16", 2, Integer.class, new UInt16Translator());
        EnumType enumType = new EnumType("BigUnit", uint16, values());

        assertThat(enumType.getSizeInBytes()).isEqualTo(2);

        ByteBuffer buffer = ByteBuffer.allocate(2);
        enumType.getTranslator().toBytes("FAHRENHEIT", buffer);
        buffer.flip();

        assertThat(enumType.getTranslator().fromBytes(buffer)).isEqualTo("FAHRENHEIT");
    }
}
