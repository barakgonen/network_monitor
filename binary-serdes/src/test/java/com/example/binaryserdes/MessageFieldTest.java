package com.example.binaryserdes;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFieldTest {

    @Test
    void constructorAndGettersWork() {
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
        MessageField<Integer> field = new MessageField<>("age", type);

        assertThat(field.getFieldName()).isEqualTo("age");
        assertThat(field.getType()).isSameAs(type);
    }
}
