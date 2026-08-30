package com.example.schemas.greeting;

import com.example.schemacore.reflect.ReflectiveMessageDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GreetingMessageTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "hi", "Hello from (32.0853, 34.7818)"})
    void toByteArray_thenFromByteBuffer_roundTripsIdAndText(String text) {
        GreetingMessage message = new GreetingMessage(7, text);

        byte[] bytes = message.toByteArray();
        GreetingMessage decoded = GreetingMessage.fromByteBuffer(ByteBuffer.wrap(bytes));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void fromByteBuffer_withTooFewBytes_throws() {
        assertThatThrownBy(() -> GreetingMessage.fromByteBuffer(ByteBuffer.wrap(new byte[]{1, 2, 3})))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromByteBuffer_withInvalidTextLength_throws() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Integer.BYTES);
        buffer.putInt(1);
        buffer.putInt(999); // claims 999 text bytes but none are present

        assertThatThrownBy(() -> GreetingMessage.fromByteBuffer(ByteBuffer.wrap(buffer.array())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reflectiveMessageDefinition_decodesAndEncodesConsistently() throws Exception {
        ReflectiveMessageDefinition definition =
                new ReflectiveMessageDefinition("Greeting Interface", "Greeting", 5002, GreetingMessage.class);

        byte[] body = definition.encodeBody(Map.of("id", 1, "text", "hello"));
        GreetingMessage decoded = (GreetingMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new GreetingMessage(1, "hello"));
        assertThat(definition.decodeBody(ByteBuffer.wrap(body)))
                .containsEntry("id", 1)
                .containsEntry("text", "hello");
    }
}
