package com.example.schemacore.binaryserdes;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeTest {

    @Test
    void simpleConstructorShouldStoreFields() {
        Translator<Integer> translator = new Translator<>() {
            @Override
            public Integer fromBytes(ByteBuffer buffer) {
                return buffer.getInt();
            }

            @Override
            public void toBytes(Integer value, ByteBuffer buffer) {
                buffer.putInt(value);
            }
        };

        Type<Integer> type = new Type<>("int32", 4, translator);

        assertThat(type.getName()).isEqualTo("int32");
        assertThat(type.getSizeInBytes()).isEqualTo(4);
        assertThat(type.getJavaType()).isNull();
        assertThat(type.getTranslator()).isSameAs(translator);
    }

    @Test
    void fullConstructorShouldStoreJavaType() {
        Translator<String> translator = new Translator<>() {
            @Override
            public String fromBytes(ByteBuffer buffer) {
                return "x";
            }

            @Override
            public void toBytes(String value, ByteBuffer buffer) {
                buffer.put((byte) 1);
            }
        };

        Type<String> type = new Type<>("str", 1, String.class, translator);

        assertThat(type.getName()).isEqualTo("str");
        assertThat(type.getSizeInBytes()).isEqualTo(1);
        assertThat(type.getJavaType()).isEqualTo(String.class);
        assertThat(type.getTranslator()).isSameAs(translator);
    }

    @Test
    void fullConstructorShouldRejectAbstractClass() {
        Translator<Number> translator = new Translator<>() {
            @Override
            public Number fromBytes(ByteBuffer buffer) {
                return 0;
            }

            @Override
            public void toBytes(Number value, ByteBuffer buffer) {
                buffer.putInt(value.intValue());
            }
        };

        assertThatThrownBy(() -> new Type<>("num", 4, Number.class, translator))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
