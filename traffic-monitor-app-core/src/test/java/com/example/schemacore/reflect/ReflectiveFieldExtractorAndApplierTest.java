package com.example.schemacore.reflect;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectiveFieldExtractorAndApplierTest {

    enum Condition {
        SUNNY("sunny"),
        CLOUDY("cloudy");

        private final String wireName;

        Condition(String wireName) {
            this.wireName = wireName;
        }

        public String getWireName() {
            return wireName;
        }
    }

    enum PlainEnum {
        FIRST,
        SECOND
    }

    record WithWireNameEnum(String stationId, Condition condition) {
    }

    record WithPlainEnum(PlainEnum status) {
    }

    record WithNumericFields(int id, double weight, long counter) {
    }

    public static class Nested {
        private int value;

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class WithNestedField {
        private Nested inner = new Nested();
        private String label;

        public Nested getInner() {
            return inner;
        }

        public void setInner(Nested inner) {
            this.inner = inner;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    record WithNestedRecordField(Nested inner, String label) {
    }

    @Test
    void extractFields_usesGetWireName_whenEnumExposesIt() throws Exception {
        Map<String, Object> fields = ReflectiveFieldExtractor.extractFields(
                new WithWireNameEnum("s1", Condition.CLOUDY));

        assertThat(fields).containsEntry("condition", "cloudy");
    }

    @Test
    void extractFields_fallsBackToEnumName_whenNoGetWireName() throws Exception {
        Map<String, Object> fields = ReflectiveFieldExtractor.extractFields(new WithPlainEnum(PlainEnum.SECOND));

        assertThat(fields).containsEntry("status", "SECOND");
    }

    @Test
    void build_acceptsWireNameCaseInsensitively_whenEnumExposesGetWireName() throws Exception {
        WithWireNameEnum built = ReflectiveFieldApplier.build(
                WithWireNameEnum.class, Map.of("stationId", "s2", "condition", "SUNNY"));

        assertThat(built.condition()).isEqualTo(Condition.SUNNY);
    }

    @Test
    void build_acceptsJavaConstantName_whenNoGetWireName() throws Exception {
        WithPlainEnum built = ReflectiveFieldApplier.build(WithPlainEnum.class, Map.of("status", "FIRST"));

        assertThat(built.status()).isEqualTo(PlainEnum.FIRST);
    }

    @Test
    void build_coercesStringValues_toNumericFieldTypes() throws Exception {
        // Regression test: HTML form inputs (and generic JSON clients) send every field as a
        // string; targets with primitive numeric types must still be built correctly.
        WithNumericFields built = ReflectiveFieldApplier.build(
                WithNumericFields.class, Map.of("id", "42", "weight", "123.5", "counter", "9999999999"));

        assertThat(built.id()).isEqualTo(42);
        assertThat(built.weight()).isEqualTo(123.5);
        assertThat(built.counter()).isEqualTo(9999999999L);
    }

    @Test
    void build_regroupsDottedKeys_intoNestedSetterBasedField() throws Exception {
        // Regression test: the generic publisher UI flattens nested/complex fields to dotted
        // paths (e.g. "inner.value"); build() must regroup those back into a nested object
        // rather than looking for a literal "inner.value" setter.
        WithNestedField built = ReflectiveFieldApplier.build(
                WithNestedField.class, Map.of("inner.value", "7", "label", "abc"));

        assertThat(built.getInner().getValue()).isEqualTo(7);
        assertThat(built.getLabel()).isEqualTo("abc");
    }

    @Test
    void build_regroupsDottedKeys_intoNestedRecordField() throws Exception {
        WithNestedRecordField built = ReflectiveFieldApplier.build(
                WithNestedRecordField.class, Map.of("inner.value", "9", "label", "xyz"));

        assertThat(built.inner().getValue()).isEqualTo(9);
        assertThat(built.label()).isEqualTo("xyz");
    }
}
