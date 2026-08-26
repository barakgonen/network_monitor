package com.example.proxy.relay;

import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.example.proxy.mirror.UdpMirrorSender;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * NAT-table style bidirectional UDP relay. Each distinct producer (client address+port) gets its
 * own ephemeral outbound socket connected to {@code destination}; a reply-pump thread relays
 * anything the destination sends back on that socket to the original producer. Every request and
 * every reply is additionally duplicated (fire-and-forget) to {@code mirror}.
 */
public class UdpRelay implements Relay {

    private static final int BUFFER_SIZE = 65_507;

    private final RelayEntry config;
    private final UdpMirrorSender mirrorSender = new UdpMirrorSender();
    private final ConcurrentHashMap<ClientKey, NatEntry> natTable = new ConcurrentHashMap<>();

    private DatagramSocket listenSocket;
    private Thread requestThread;
    private ScheduledExecutorService sweepExecutor;
    private volatile boolean running;

    public UdpRelay(RelayEntry config) {
        this.config = config;
    }

    @Override
    public void start() throws SocketException {
        EndpointConfig listen = config.getListen();
        listenSocket = new DatagramSocket(new InetSocketAddress(listen.getHost(), listen.getPort()));
        running = true;

        requestThread = new Thread(this::requestLoop, "proxy-udp-" + config.getKey());
        requestThread.setDaemon(true);
        requestThread.start();

        sweepExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "proxy-udp-nat-sweep-" + config.getKey());
            t.setDaemon(true);
            return t;
        });
        long sweepInterval = config.getNatIdleSweepIntervalMillis();
        sweepExecutor.scheduleAtFixedRate(this::sweepIdleEntries, sweepInterval, sweepInterval, TimeUnit.MILLISECONDS);

        System.out.println("[" + config.getKey() + "] UDP relay listening on " + listen.getHost() + ":" + listen.getPort()
                + " -> destination " + config.getDestination().getHost() + ":" + config.getDestination().getPort()
                + " (mirror " + config.getMirror().getHost() + ":" + config.getMirror().getPort() + ")");
    }

    private void requestLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running && !listenSocket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                listenSocket.receive(packet);
            } catch (IOException e) {
                if (!listenSocket.isClosed()) {
                    System.err.println("[" + config.getKey() + "] UDP relay receive error: " + e.getMessage());
                }
                continue;
            }

            byte[] payload = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), payload, 0, packet.getLength());

            ClientKey clientKey = new ClientKey(packet.getAddress(), packet.getPort());
            NatEntry entry = natTable.computeIfAbsent(clientKey, this::createNatEntry);
            if (entry == null) {
                continue;
            }
            entry.touch();

            try {
                entry.outboundSocket().send(new DatagramPacket(payload, payload.length));
            } catch (IOException e) {
                System.err.println("[" + config.getKey() + "] UDP relay forward to destination failed: " + e.getMessage());
            }

            mirrorSender.send(config.getMirror().getHost(), config.getMirror().getPort(), payload, config.getKey());
        }
    }

    private NatEntry createNatEntry(ClientKey clientKey) {
        EndpointConfig destination = config.getDestination();
        DatagramSocket outboundSocket;
        try {
            outboundSocket = new DatagramSocket();
            outboundSocket.connect(InetAddress.getByName(destination.getHost()), destination.getPort());
        } catch (IOException e) {
            System.err.println("[" + config.getKey() + "] failed to open outbound socket to destination: " + e.getMessage());
            return null;
        }

        NatEntry entry = new NatEntry(outboundSocket);
        Thread replyPump = new Thread(() -> replyPumpLoop(clientKey, entry), "proxy-udp-reply-" + config.getKey());
        replyPump.setDaemon(true);
        entry.setReplyPumpThread(replyPump);
        replyPump.start();
        return entry;
    }

    private void replyPumpLoop(ClientKey clientKey, NatEntry entry) {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running && !entry.outboundSocket().isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                entry.outboundSocket().receive(packet);
            } catch (IOException e) {
                if (!entry.outboundSocket().isClosed()) {
                    System.err.println("[" + config.getKey() + "] UDP relay reply receive error: " + e.getMessage());
                }
                return;
            }

            byte[] replyBytes = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), replyBytes, 0, packet.getLength());
            entry.touch();

            try {
                listenSocket.send(new DatagramPacket(replyBytes, replyBytes.length, clientKey.address(), clientKey.port()));
            } catch (IOException e) {
                System.err.println("[" + config.getKey() + "] UDP relay reply forward to producer failed: " + e.getMessage());
            }

            mirrorSender.send(config.getMirror().getHost(), config.getMirror().getPort(), replyBytes, config.getKey());
        }
    }

    private void sweepIdleEntries() {
        long idleTimeout = config.getNatIdleTimeoutMillis();
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<ClientKey, NatEntry>> it = natTable.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ClientKey, NatEntry> mapEntry = it.next();
            if (now - mapEntry.getValue().lastActivityEpochMillis() > idleTimeout) {
                it.remove();
                mapEntry.getValue().outboundSocket().close();
            }
        }
    }

    /** Package-private, for test visibility only. */
    int natTableSize() {
        return natTable.size();
    }

    @Override
    public void stop() {
        running = false;
        if (listenSocket != null) {
            listenSocket.close();
        }
        if (requestThread != null) {
            requestThread.interrupt();
        }
        if (sweepExecutor != null) {
            sweepExecutor.shutdownNow();
        }
        for (NatEntry entry : natTable.values()) {
            entry.outboundSocket().close();
        }
        natTable.clear();
    }

    private record ClientKey(InetAddress address, int port) {
    }

    private static final class NatEntry {
        private final DatagramSocket outboundSocket;
        private volatile Thread replyPumpThread;
        private volatile long lastActivityEpochMillis = System.currentTimeMillis();

        private NatEntry(DatagramSocket outboundSocket) {
            this.outboundSocket = outboundSocket;
        }

        DatagramSocket outboundSocket() {
            return outboundSocket;
        }

        void setReplyPumpThread(Thread thread) {
            this.replyPumpThread = thread;
        }

        void touch() {
            lastActivityEpochMillis = System.currentTimeMillis();
        }

        long lastActivityEpochMillis() {
            return lastActivityEpochMillis;
        }
    }
}
