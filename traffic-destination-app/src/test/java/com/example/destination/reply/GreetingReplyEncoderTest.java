package com.example.destination.reply;

import com.example.schemacore.envelope.ProtocolHeader;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemas.greeting.BeaconMessage;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GreetingReplyEncoderTest {

    @Test
    void buildGreetingReply_decodesBeacon_andEncodesMatchingGreeting() {
        BeaconMessage beacon = new BeaconMessage(32.0853, 34.7818);
        byte[] beaconBody = ReflectiveStructCodec.encode(beacon);
        byte[] beaconWireBytes = ProtocolHeaderCodec.encodeMessage(5001, System.currentTimeMillis(), beaconBody);

        byte[] greetingWireBytes = GreetingReplyEncoder.buildGreetingReply(beaconWireBytes);

        ByteBuffer buffer = ByteBuffer.wrap(greetingWireBytes);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);
        assertThat(header.opcode()).isEqualTo(5002);

        byte[] greetingBody = new byte[header.bodyLength()];
        buffer.get(greetingBody);
        ByteBuffer bodyBuffer = ByteBuffer.wrap(greetingBody);
        assertThat(bodyBuffer.getInt()).isEqualTo(1);
        int textLength = bodyBuffer.getInt();
        byte[] textBytes = new byte[textLength];
        bodyBuffer.get(textBytes);
        assertThat(new String(textBytes, java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("Hello from (32.0853, 34.7818)");
    }

    @Test
    void buildGreetingReply_withTooFewBytesForHeader_throws() {
        byte[] tooShort = new byte[]{1, 2, 3};

        assertThatThrownBy(() -> GreetingReplyEncoder.buildGreetingReply(tooShort))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildGreetingReply_withBodyThatIsNotABeacon_throws() {
        byte[] bogusWireBytes = ProtocolHeaderCodec.encodeMessage(5001, System.currentTimeMillis(), new byte[]{1});

        assertThatThrownBy(() -> GreetingReplyEncoder.buildGreetingReply(bogusWireBytes))
                .isInstanceOf(RuntimeException.class);
    }
}
