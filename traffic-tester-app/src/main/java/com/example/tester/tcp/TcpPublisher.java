package com.example.tester.tcp;

import java.io.IOException;
import java.net.Socket;

public class TcpPublisher {
    public void send(String host, int port, byte[] payload) {
        try (Socket socket = new Socket(host, port)) {
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send TCP message to " + host + ":" + port, e);
        }
    }
}
