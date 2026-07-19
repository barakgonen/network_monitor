package com.example.schemacore;

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

    record WithWireNameEnum(String stationId, Condition condition) implements ProtocolMessage {
    }

    record WithPlainEnum(PlainEnum status) implements ProtocolMessage {
    }

    record WithNumericFields(int id, double weight, long counter) implements ProtocolMessage {
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
}
