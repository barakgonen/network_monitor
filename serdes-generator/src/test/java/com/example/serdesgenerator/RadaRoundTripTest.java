package com.example.serdesgenerator;

import com.example.binaryserdes.ProtocolIn;
import com.example.binaryserdes.config.ProtocolConfig;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemacore.reflect.StructSizeCalculator;
import com.example.tester.schemas.rada.messages.RadaExtendedStatus;
import com.example.tester.schemas.rada.messages.RadaExtendedStatusMrs;
import com.example.tester.schemas.rada.messages.RadaStatus;
import com.example.tester.schemas.rada.messages.RadaTracksExtended;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * Cross-checks the generator against traffic-tester-app's own wire encoding: encode a real rada
 * message via {@link ReflectiveStructCodec} (the exact path {@code PayloadFactory} uses for
 * synthetic test traffic), decode the very same bytes using a {@code protocol.json} generated
 * in-memory by {@link ProtocolJsonGenerator}, and assert every field survives the round trip.
 * This is the strongest available correctness proof short of running the real monitor app.
 */
class RadaRoundTripTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void radaStatusRoundTrips() throws Exception {
        RadaStatus message = Instancio.create(RadaStatus.class);
        message.getHeader().setMsgType(3);
        assertRoundTrips(message, RadaStatus.class, 3);
    }

    @Test
    void radaExtendedStatusRoundTrips() throws Exception {
        RadaExtendedStatus message = Instancio.create(RadaExtendedStatus.class);
        message.getHeader().setMsgType(1);
        assertRoundTrips(message, RadaExtendedStatus.class, 1);
    }

    @Test
    void radaExtendedStatusMrsRoundTrips() throws Exception {
        RadaExtendedStatusMrs message = Instancio.create(RadaExtendedStatusMrs.class);
        message.getHeader().setMsgType(2);
        assertRoundTrips(message, RadaExtendedStatusMrs.class, 2);
    }

    @Test
    void radaTracksExtendedRoundTrips() throws Exception {
        // Same Instancio.ignore(...) workaround PayloadFactory.createRadaTracksExtended uses:
        // Instancio doesn't understand @FixedArrayLength and would otherwise generate
        // differently-sized arrays, breaking both the real codec and this test.
        RadaTracksExtended message = Instancio.of(RadaTracksExtended.class)
                .ignore(field(RadaTracksExtended.class, "trackData"))
                .ignore(field(RadaTracksExtended.class, "plotData"))
                .create();
        message.getHeader().setMsgType(4);
        assertRoundTrips(message, RadaTracksExtended.class, 4);
    }

    private void assertRoundTrips(Object message, Class<?> messageClass, int opcode) throws Exception {
        ProtocolConfig config = new ProtocolJsonGenerator().generate(
                List.of(new ProtocolJsonGenerator.RootMessage(messageClass, opcode)));

        byte[] bytes = ReflectiveStructCodec.encode(message);

        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(config);
        String json = protocolIn.parse(opcode, bytes);
        JsonNode node = mapper.readTree(json);

        assertFieldsMatch(message, node, messageClass);
    }

    private void assertFieldsMatch(Object instance, JsonNode node, Class<?> type) throws Exception {
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(instance);
            JsonNode fieldNode = node.get(field.getName());
            assertThat(fieldNode)
                    .as("missing field '%s' in decoded JSON for %s", field.getName(), type.getName())
                    .isNotNull();
            assertValueMatches(field.getType(), value, fieldNode, field.getName());
        }
    }

    private void assertValueMatches(Class<?> valueType, Object value, JsonNode node, String path) throws Exception {
        if (valueType.isArray()) {
            assertThat(node.isArray()).as("expected array at %s", path).isTrue();
            int length = Array.getLength(value);
            assertThat(node.size()).as("array length at %s", path).isEqualTo(length);
            Class<?> componentType = valueType.getComponentType();
            for (int i = 0; i < length; i++) {
                assertValueMatches(componentType, Array.get(value, i), node.get(i), path + "[" + i + "]");
            }
            return;
        }

        if (valueType.isEnum()) {
            assertThat(node.asText()).as("enum name at %s", path).isEqualTo(((Enum<?>) value).name());
            return;
        }

        if (StructSizeCalculator.isFixedScalar(valueType)) {
            assertScalarMatches(valueType, value, node, path);
            return;
        }

        assertFieldsMatch(value, node, valueType);
    }

    private void assertScalarMatches(Class<?> valueType, Object value, JsonNode node, String path) {
        if (valueType == byte.class || valueType == Byte.class) {
            assertThat(node.asInt()).as(path).isEqualTo(Byte.toUnsignedInt((Byte) value));
        } else if (valueType == short.class || valueType == Short.class) {
            assertThat(node.asInt()).as(path).isEqualTo(Short.toUnsignedInt((Short) value));
        } else if (valueType == char.class || valueType == Character.class) {
            assertThat(node.asInt()).as(path).isEqualTo((int) (char) value);
        } else if (valueType == int.class || valueType == Integer.class) {
            assertThat(node.asInt()).as(path).isEqualTo((Integer) value);
        } else if (valueType == long.class || valueType == Long.class) {
            assertThat(node.asLong()).as(path).isEqualTo((Long) value);
        } else if (valueType == float.class || valueType == Float.class) {
            assertThat(node.floatValue()).as(path).isEqualTo((Float) value);
        } else if (valueType == double.class || valueType == Double.class) {
            assertThat(node.doubleValue()).as(path).isEqualTo((Double) value);
        } else {
            throw new IllegalArgumentException("Unhandled scalar type in test comparator: " + valueType);
        }
    }
}
