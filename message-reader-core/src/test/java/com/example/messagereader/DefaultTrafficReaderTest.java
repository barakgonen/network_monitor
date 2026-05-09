package com.example.messagereader;

import com.example.messagereader.api.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTrafficReaderTest {

    public static class Header {
        private short opcode;

        public Header() {
        }

        public Header(ByteBuffer buffer) {
            this.opcode = buffer.getShort();
        }

        public short getOpcode() {
            return opcode;
        }
    }

    public static class DemoMessage {
        private Header header;
        private int value;

        public DemoMessage() {
        }

        public DemoMessage(ByteBuffer buffer) {
            this.header = new Header(buffer);
            this.value = buffer.getInt();
        }

        public Header getHeader() {
            return header;
        }

        public int getValue() {
            return value;
        }
    }

    @Test
    void parsesRawPacketIntoParsedTrafficMessage() throws Exception {
        TrafficInterfaceDefinition definition = new TrafficInterfaceDefinition();
        definition.setName("Demo Interface");
        definition.setProtocol(TransportProtocol.UDP);
        definition.setPort(5050);
        definition.setHeaderType(Header.class.getName());
        definition.setOpcodeFieldName("opcode");
        definition.setSupportedMessages(Map.of(
                "7", new TrafficMessageDefinition(DemoMessage.class.getName(), "DemoMessage")
        ));

        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES + Integer.BYTES);
        buffer.putShort((short) 7);
        buffer.putInt(123);

        RawTrafficPacket packet = new RawTrafficPacket(
                TransportProtocol.UDP,
                5050,
                "localhost:12345",
                buffer.array(),
                Instant.now()
        );

        ArrayBlockingQueue<ParsedTrafficMessage> queue = new ArrayBlockingQueue<>(1);
        new com.example.messagereader.reflection.ReflectionTrafficMessageParser()
                .parse(packet, definition);

        ParsedTrafficMessage parsed = new com.example.messagereader.reflection.ReflectionTrafficMessageParser()
                .parse(packet, definition);

        assertTrue(parsed.parsed());
        assertEquals("Demo Interface", parsed.interfaceName());
        assertEquals("DemoMessage", parsed.messageName());
        assertEquals(7, ((Number) parsed.opcode()).intValue());
        assertEquals(123, ((Number) parsed.bodyFields().get("value")).intValue());
    }
}
