package com.example.serdesgenerator;

import com.example.binaryserdes.config.MessageConfig;
import com.example.binaryserdes.config.ProtocolConfig;
import com.example.binaryserdes.config.TypeConfig;
import com.example.serdesgenerator.fixtures.AllScalarTypesMessage;
import com.example.serdesgenerator.fixtures.ArrayLengthInferredMessage;
import com.example.serdesgenerator.fixtures.ArrayMissingAnnotationMessage;
import com.example.serdesgenerator.fixtures.ArrayOfEnumsMessage;
import com.example.serdesgenerator.fixtures.ArrayOfScalarsMessage;
import com.example.serdesgenerator.fixtures.ArrayOfStructsMessage;
import com.example.serdesgenerator.fixtures.BooleanFieldMessage;
import com.example.serdesgenerator.fixtures.EnumWithCodeMessage;
import com.example.serdesgenerator.fixtures.EnumWithoutCodeMessage;
import com.example.serdesgenerator.fixtures.SelfReferentialMessage;
import com.example.serdesgenerator.fixtures.StringFieldMessage;
import com.example.serdesgenerator.fixtures.WrapperMessageA;
import com.example.serdesgenerator.fixtures.WrapperMessageB;
import com.example.serdesgenerator.fixtures.collision.CollisionMessageA;
import com.example.serdesgenerator.fixtures.collision.CollisionMessageB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolJsonGeneratorTest {

    private final ProtocolJsonGenerator generator = new ProtocolJsonGenerator();

    @Test
    void mapsAllScalarTypes() {
        ProtocolConfig config = generateOne(AllScalarTypesMessage.class);

        MessageConfig mc = onlyMessage(config);
        assertThat(fieldTypes(mc)).containsExactly(
                "uint8", "uint16", "uint16", "int32", "int64", "float32", "double64");
        assertThat(config.types).isEmpty();
    }

    @Test
    void booleanFieldIsUnsupported() {
        assertThatThrownBy(() -> generateOne(BooleanFieldMessage.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("boolean");
    }

    @Test
    void stringFieldIsUnsupported() {
        assertThatThrownBy(() -> generateOne(StringFieldMessage.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("String");
    }

    @Test
    void enumWithGetCodeUsesItsWireCodes() {
        ProtocolConfig config = generateOne(EnumWithCodeMessage.class);

        TypeConfig enumType = typeNamed(config, "CodedEnum");
        assertThat(enumType.kind).isEqualTo("enum");
        assertThat(enumType.underlyingType).isEqualTo("int32");
        assertThat(enumType.values).containsExactly(Map.entry("A", 1), Map.entry("B", 5));
    }

    @Test
    void enumWithoutGetCodeFallsBackToOrdinalAndRespectsEnumWireSize() {
        ProtocolConfig config = generateOne(EnumWithoutCodeMessage.class);

        TypeConfig enumType = typeNamed(config, "PlainEnum");
        assertThat(enumType.underlyingType).isEqualTo("uint8");
        assertThat(enumType.values).containsExactly(
                Map.entry("X", 0), Map.entry("Y", 1), Map.entry("Z", 2));
    }

    @Test
    void arrayOfScalarsIsNamedByElementAndLength() {
        ProtocolConfig config = generateOne(ArrayOfScalarsMessage.class);

        TypeConfig arrayType = typeNamed(config, "uint8Array5");
        assertThat(arrayType.kind).isEqualTo("array");
        assertThat(arrayType.elementType).isEqualTo("uint8");
        assertThat(arrayType.length).isEqualTo(5);
    }

    @Test
    void arrayMissingFixedArrayLengthThrows() {
        assertThatThrownBy(() -> generateOne(ArrayMissingAnnotationMessage.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@FixedArrayLength");
    }

    @Test
    void arrayMissingFixedArrayLengthInfersFromDefaultConstructor() {
        ProtocolConfig config = generateOne(ArrayLengthInferredMessage.class);

        TypeConfig arrayType = typeNamed(config, "uint8Array5");
        assertThat(arrayType.kind).isEqualTo("array");
        assertThat(arrayType.elementType).isEqualTo("uint8");
        assertThat(arrayType.length).isEqualTo(5);
    }

    @Test
    void arrayOfStructsRegistersBothRecordAndArrayTypes() {
        ProtocolConfig config = generateOne(ArrayOfStructsMessage.class);

        TypeConfig recordType = typeNamed(config, "NestedStruct");
        assertThat(recordType.kind).isEqualTo("record");
        assertThat(fieldTypes(recordType)).containsExactly("int32", "int32");

        TypeConfig arrayType = typeNamed(config, "NestedStructArray3");
        assertThat(arrayType.elementType).isEqualTo("NestedStruct");
        assertThat(arrayType.length).isEqualTo(3);
    }

    @Test
    void arrayOfEnumsRegistersEnumThenArray() {
        ProtocolConfig config = generateOne(ArrayOfEnumsMessage.class);

        TypeConfig enumType = typeNamed(config, "CodedEnum");
        assertThat(enumType.underlyingType).isEqualTo("uint8");

        TypeConfig arrayType = typeNamed(config, "CodedEnumArray2");
        assertThat(arrayType.elementType).isEqualTo("CodedEnum");
        assertThat(arrayType.length).isEqualTo(2);
    }

    @Test
    void nestedStructReferencedByTwoMessagesIsOnlyEmittedOnce() {
        ProtocolConfig config = generator.generate(List.of(
                new ProtocolJsonGenerator.RootMessage(WrapperMessageA.class, 1),
                new ProtocolJsonGenerator.RootMessage(WrapperMessageB.class, 2)));

        assertThat(config.messages).hasSize(2);
        long nestedStructCount = config.types.stream().filter(t -> t.name.equals("NestedStruct")).count();
        assertThat(nestedStructCount).isEqualTo(1);
    }

    @Test
    void selfReferentialStructThrows() {
        assertThatThrownBy(() -> generateOne(SelfReferentialMessage.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Recursive struct detected");
    }

    @Test
    void twoUnrelatedClassesSharingASimpleNameThrows() {
        assertThatThrownBy(() -> generator.generate(List.of(
                new ProtocolJsonGenerator.RootMessage(CollisionMessageA.class, 1),
                new ProtocolJsonGenerator.RootMessage(CollisionMessageB.class, 2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Type name collision");
    }

    private ProtocolConfig generateOne(Class<?> messageClass) {
        return generator.generate(List.of(new ProtocolJsonGenerator.RootMessage(messageClass, 1)));
    }

    private MessageConfig onlyMessage(ProtocolConfig config) {
        assertThat(config.messages).hasSize(1);
        return config.messages.get(0);
    }

    private TypeConfig typeNamed(ProtocolConfig config, String name) {
        return config.types.stream()
                .filter(t -> t.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No type named '" + name + "' in generated config. Types: "
                        + config.types.stream().map(t -> t.name).toList()));
    }

    private List<String> fieldTypes(MessageConfig mc) {
        return mc.fields.stream().map(f -> f.type).toList();
    }

    private List<String> fieldTypes(TypeConfig tc) {
        return tc.fields.stream().map(f -> f.type).toList();
    }
}
