package com.example.destination.reply;

import com.example.schemacore.envelope.ProtocolHeader;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemas.ping.PingMessage;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PongReplyEncoderTest {

    @Test
    void buildPongReply_decodesPing_andEncodesMatchingPong() {
        PingMessage ping = new PingMessage(42);
        byte[] pingBody = ReflectiveStructCodec.encode(ping);
        byte[] pingWireBytes = ProtocolHeaderCodec.encodeMessage(3001, System.currentTimeMillis(), pingBody);

        byte[] pongWireBytes = PongReplyEncoder.buildPongReply(pingWireBytes);

        ByteBuffer buffer = ByteBuffer.wrap(pongWireBytes);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);
        assertThat(header.opcode()).isEqualTo(3002);

        byte[] pongBody = new byte[header.bodyLength()];
        buffer.get(pongBody);
        assertThat(pongBody).hasSize(Integer.BYTES);
        assertThat(ByteBuffer.wrap(pongBody).getInt()).isEqualTo(42);
    }

    @Test
    void buildPongReply_withNegativeSequence_stillRoundTrips() {
        PingMessage ping = new PingMessage(-7);
        byte[] pingBody = ReflectiveStructCodec.encode(ping);
        byte[] pingWireBytes = ProtocolHeaderCodec.encodeMessage(3001, System.currentTimeMillis(), pingBody);

        byte[] pongWireBytes = PongReplyEncoder.buildPongReply(pingWireBytes);

        ByteBuffer buffer = ByteBuffer.wrap(pongWireBytes);
        ProtocolHeaderCodec.decodeHeader(buffer);
        assertThat(buffer.getInt()).isEqualTo(-7);
    }

    @Test
    void buildPongReply_withTooFewBytesForHeader_throws() {
        byte[] tooShort = new byte[]{1, 2, 3};

        assertThatThrownBy(() -> PongReplyEncoder.buildPongReply(tooShort))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildPongReply_withBodyThatIsNotAPing_throws() {
        // Valid envelope framing, but a body far too short to be a PingMessage's 4-byte int field.
        byte[] bogusWireBytes = ProtocolHeaderCodec.encodeMessage(3001, System.currentTimeMillis(), new byte[]{1});

        assertThatThrownBy(() -> PongReplyEncoder.buildPongReply(bogusWireBytes))
                .isInstanceOf(RuntimeException.class);
    }
}
