package com.example.destination.reply;

import com.example.binaryserdes.envelope.ProtocolHeader;
import com.example.binaryserdes.envelope.ProtocolHeaderCodec;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GreetingReplyEncoderTest {

    /** Beacon's body is two double64 fields (serdes/greeting.protocol.json) - no message class needed to build it. */
    private static byte[] beaconWireBytes(double lat, double lon) {
        ByteBuffer body = ByteBuffer.allocate(Double.BYTES + Double.BYTES);
        body.putDouble(lat);
        body.putDouble(lon);
        return ProtocolHeaderCodec.encodeMessage(5001, System.currentTimeMillis(), body.array());
    }

    @Test
    void buildGreetingReply_decodesBeacon_andEncodesMatchingGreeting() {
        byte[] beaconWireBytes = beaconWireBytes(32.0853, 34.7818);

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
