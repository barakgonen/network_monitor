package com.example.destination.listener;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

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
