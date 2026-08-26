package com.example.destination.listener;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

class TcpEchoListenerTest {

    private TcpEchoListener listener;

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.stop();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static InterfaceEntry entry(int port, ReplyMode replyMode) {
        InterfaceEntry entry = new InterfaceEntry();
        entry.setKey("test");
        entry.setProtocol("TCP");
        entry.setPort(port);
        entry.setReplyMode(replyMode);
        return entry;
    }

    @Test
    void echoMode_writesSameBytesBackOnSameConnection() throws Exception {
        int port = freePort();
        listener = new TcpEchoListener(entry(port, ReplyMode.ECHO));
        listener.start();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(2000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            byte[] payload = "hello".getBytes();
            out.write(payload);
            out.flush();

            byte[] buffer = new byte[1024];
            int read = in.read(buffer);

            assertThat(new String(buffer, 0, read)).isEqualTo("hello");
        }
    }

    @Test
    void noneMode_doesNotReply() throws Exception {
        int port = freePort();
        listener = new TcpEchoListener(entry(port, ReplyMode.NONE));
        listener.start();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(500);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            byte[] payload = "hello".getBytes();
            out.write(payload);
            out.flush();

            byte[] buffer = new byte[1024];
            assertThatThrowsTimeout(() -> in.read(buffer));
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertThatThrowsTimeout(ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            throw new AssertionError("Expected a read timeout but none occurred");
        } catch (java.net.SocketTimeoutException expected) {
            // expected: no echo arrived
        }
    }
}
