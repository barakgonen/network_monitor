package com.example.proxy.mirror;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * One persistent outbound TCP connection to the mirror target, shared across every client
 * connection a {@code TcpRelay} handles for its whole lifetime. A single ordered connection
 * preserves the monitor's header+bodyLength framing (it reads header/body, header/body, ...
 * indefinitely off one stream), whereas reconnecting per chunk or per client connection would
 * scatter one logical message's bytes across multiple mirror connections and break framing.
 *
 * <p>Best-effort: mirror failures are caught/logged here and never propagate to the real splice
 * path.</p>
 */
public class TcpMirrorConnection implements AutoCloseable {

    private final String host;
    private final int port;
    private final String relayKey;
    private Socket socket;
    private OutputStream out;

    public TcpMirrorConnection(String host, int port, String relayKey) {
        this.host = host;
        this.port = port;
        this.relayKey = relayKey;
    }

    public synchronized void ensureConnected() {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5_000);
            out = socket.getOutputStream();
        } catch (IOException e) {
            System.err.println("[" + relayKey + "] mirror TCP connect to " + host + ":" + port + " failed: " + e.getMessage());
            socket = null;
            out = null;
        }
    }

    public synchronized void write(byte[] buf, int len) {
        ensureConnected();
        if (out == null) {
            return;
        }
        try {
            out.write(buf, 0, len);
            out.flush();
        } catch (IOException e) {
            System.err.println("[" + relayKey + "] mirror TCP write to " + host + ":" + port + " failed: " + e.getMessage());
            closeQuietly();
        }
    }

    private void closeQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;
        out = null;
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }
}
