package com.example.destination.listener;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import com.example.binaryserdes.envelope.ProtocolHeader;
import com.example.binaryserdes.envelope.ProtocolHeaderCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class UdpEchoListenerTest {

    private UdpEchoListener listener;

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.stop();
        }
    }

    private static int freePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Ping's body is just a single int32 sequence field (serdes/ping.protocol.json) - no message class needed to build it. */
    private static byte[] pingWireBytes(int sequence) {
        ByteBuffer body = ByteBuffer.allocate(Integer.BYTES);
        body.putInt(sequence);
        return ProtocolHeaderCodec.encodeMessage(3001, System.currentTimeMillis(), body.array());
    }

    private static InterfaceEntry entry(int port, ReplyMode replyMode) {
        InterfaceEntry entry = new InterfaceEntry();
        entry.setKey("test");
        entry.setProtocol("UDP");
        entry.setPort(port);
        entry.setReplyMode(replyMode);
        return entry;
    }

    @Test
    void echoMode_sendsSameBytesBackToSender() throws Exception {
        int port = freePort();
        listener = new UdpEchoListener(entry(port, ReplyMode.ECHO));
        listener.start();

        try (DatagramSocket senderSocket = new DatagramSocket()) {
            senderSocket.setSoTimeout(2000);
            byte[] payload = "hello".getBytes();
            senderSocket.send(new DatagramPacket(payload, payload.length, InetAddress.getLoopbackAddress(), port));

            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            senderSocket.receive(reply);

            assertThat(new String(reply.getData(), 0, reply.getLength())).isEqualTo("hello");
        }
    }

    @Test
    void pongMode_repliesWithRealPongForAPing() throws Exception {
        int port = freePort();
        listener = new UdpEchoListener(entry(port, ReplyMode.PONG));
        listener.start();

        try (DatagramSocket senderSocket = new DatagramSocket()) {
            senderSocket.setSoTimeout(2000);

            byte[] pingWireBytes = pingWireBytes(7);
            senderSocket.send(new DatagramPacket(pingWireBytes, pingWireBytes.length, InetAddress.getLoopbackAddress(), port));

            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            senderSocket.receive(reply);

            byte[] pongWireBytes = new byte[reply.getLength()];
            System.arraycopy(reply.getData(), reply.getOffset(), pongWireBytes, 0, reply.getLength());

            ByteBuffer replyBuffer = ByteBuffer.wrap(pongWireBytes);
            ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(replyBuffer);
            assertThat(header.opcode()).isEqualTo(3002);

            byte[] pongBody = new byte[header.bodyLength()];
            replyBuffer.get(pongBody);
            assertThat(ByteBuffer.wrap(pongBody).getInt()).isEqualTo(7);
        }
    }

    @Test
    void pongMode_survivesGarbagePacket_andStillHandlesNextValidPing() throws Exception {
        int port = freePort();
        listener = new UdpEchoListener(entry(port, ReplyMode.PONG));
        listener.start();

        try (DatagramSocket senderSocket = new DatagramSocket()) {
            senderSocket.setSoTimeout(2000);

            // Garbage too short to even be a valid envelope header - buildPongReply throws, the
            // listener must log and keep its receive loop alive rather than dying.
            byte[] garbage = {1, 2, 3};
            senderSocket.send(new DatagramPacket(garbage, garbage.length, InetAddress.getLoopbackAddress(), port));

            // No reply should arrive for the garbage packet.
            senderSocket.setSoTimeout(300);
            byte[] noReplyBuffer = new byte[1024];
            assertThat(catchTimeout(() -> senderSocket.receive(new DatagramPacket(noReplyBuffer, noReplyBuffer.length))))
                    .isTrue();

            // A subsequent, valid Ping must still be handled correctly.
            byte[] pingWireBytes = pingWireBytes(11);
            senderSocket.send(new DatagramPacket(pingWireBytes, pingWireBytes.length, InetAddress.getLoopbackAddress(), port));

            senderSocket.setSoTimeout(2000);
            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            senderSocket.receive(reply);

            byte[] pongWireBytes = new byte[reply.getLength()];
            System.arraycopy(reply.getData(), reply.getOffset(), pongWireBytes, 0, reply.getLength());
            ByteBuffer replyBuffer = ByteBuffer.wrap(pongWireBytes);
            ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(replyBuffer);
            assertThat(header.opcode()).isEqualTo(3002);
            byte[] pongBody = new byte[header.bodyLength()];
            replyBuffer.get(pongBody);
            assertThat(ByteBuffer.wrap(pongBody).getInt()).isEqualTo(11);
        }
    }

    @Test
    void replyPort_configured_sendsReplyToFixedPortInsteadOfSenderSourcePort() throws Exception {
        int port = freePort();
        int fixedReplyPort = freePort();
        InterfaceEntry config = entry(port, ReplyMode.ECHO);
        config.setReplyPort(fixedReplyPort);
        listener = new UdpEchoListener(config);
        listener.start();

        try (DatagramSocket senderSocket = new DatagramSocket();
             DatagramSocket replyListenerSocket = new DatagramSocket(fixedReplyPort)) {
            replyListenerSocket.setSoTimeout(2000);

            byte[] payload = "hello".getBytes();
            senderSocket.send(new DatagramPacket(payload, payload.length, InetAddress.getLoopbackAddress(), port));

            // The reply lands on the fixed configured port, not back at the sender's own socket.
            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            replyListenerSocket.receive(reply);
            assertThat(new String(reply.getData(), 0, reply.getLength())).isEqualTo("hello");

            senderSocket.setSoTimeout(300);
            assertThat(catchTimeout(() -> senderSocket.receive(new DatagramPacket(new byte[1024], 1024)))).isTrue();
        }
    }

    @Test
    void noneMode_doesNotReply() throws Exception {
        int port = freePort();
        listener = new UdpEchoListener(entry(port, ReplyMode.NONE));
        listener.start();

        try (DatagramSocket senderSocket = new DatagramSocket()) {
            senderSocket.setSoTimeout(500);
            byte[] payload = "hello".getBytes();
            senderSocket.send(new DatagramPacket(payload, payload.length, InetAddress.getLoopbackAddress(), port));

            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            assertThat(catchTimeout(() -> senderSocket.receive(reply))).isTrue();
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static boolean catchTimeout(ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            return false;
        } catch (java.net.SocketTimeoutException e) {
            return true;
        }
    }
}
