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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bidirectional UDP relay, in one of two modes selected by {@link RelayEntry#getReplyPort()}:
 *
 * <ul>
 *   <li><b>Ephemeral NAT-table mode</b> (default, {@code replyPort} unset): each distinct producer
 *   (client address+port) gets its own ephemeral outbound socket connected to {@code destination};
 *   a reply-pump thread relays anything the destination sends back on that socket to the original
 *   producer. Supports multiple concurrent producers.</li>
 *   <li><b>Fixed-reply-port mode</b> ({@code replyPort} set): a single shared outbound socket is
 *   bound to that fixed local port for the relay's whole lifetime, and replies are always routed
 *   to whichever producer sent the most recent request ("last producer wins", no NAT table). Used
 *   when the destination itself replies to a fixed configured port rather than to the request's
 *   actual source port (see traffic-destination-app's {@code InterfaceEntry.replyPort}) - trades
 *   concurrent-producer isolation for a predictable reply path.</li>
 * </ul>
 *
 * Every request and every reply is additionally duplicated (fire-and-forget) to {@code mirror},
 * regardless of mode.
 */
public class UdpRelay implements Relay {

    private static final int BUFFER_SIZE = 65_507;

    private final RelayEntry config;
    private final UdpMirrorSender mirrorSender = new UdpMirrorSender();
    private final ConcurrentHashMap<ClientKey, NatEntry> natTable = new ConcurrentHashMap<>();
    private final boolean fixedReplyPortMode;

    private DatagramSocket listenSocket;
    private Thread requestThread;
    private ScheduledExecutorService sweepExecutor;
    private volatile boolean running;

    // Fixed-reply-port mode only.
    private DatagramSocket fixedOutboundSocket;
    private Thread fixedReplyPumpThread;
    private final AtomicReference<ClientKey> lastProducer = new AtomicReference<>();

    public UdpRelay(RelayEntry config) {
        this.config = config;
        this.fixedReplyPortMode = config.getReplyPort() != null;
    }

    @Override
    public void start() throws IOException {
        EndpointConfig listen = config.getListen();
        listenSocket = new DatagramSocket(new InetSocketAddress(listen.getHost(), listen.getPort()));
        running = true;

        if (fixedReplyPortMode) {
            EndpointConfig destination = config.getDestination();
            fixedOutboundSocket = new DatagramSocket(config.getReplyPort());
            fixedOutboundSocket.connect(InetAddress.getByName(destination.getHost()), destination.getPort());
            fixedReplyPumpThread = new Thread(this::fixedReplyPumpLoop, "proxy-udp-reply-" + config.getKey());
            fixedReplyPumpThread.setDaemon(true);
            fixedReplyPumpThread.start();
        } else {
            sweepExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "proxy-udp-nat-sweep-" + config.getKey());
                t.setDaemon(true);
                return t;
            });
            long sweepInterval = config.getNatIdleSweepIntervalMillis();
            sweepExecutor.scheduleAtFixedRate(this::sweepIdleEntries, sweepInterval, sweepInterval, TimeUnit.MILLISECONDS);
        }

        requestThread = new Thread(this::requestLoop, "proxy-udp-" + config.getKey());
        requestThread.setDaemon(true);
        requestThread.start();

        System.out.println("[" + config.getKey() + "] UDP relay listening on " + listen.getHost() + ":" + listen.getPort()
                + " -> destination " + config.getDestination().getHost() + ":" + config.getDestination().getPort()
                + " (mirror " + config.getMirror().getHost() + ":" + config.getMirror().getPort() + ")"
                + (fixedReplyPortMode ? " [fixed reply port " + config.getReplyPort() + "]" : ""));
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

            if (fixedReplyPortMode) {
                lastProducer.set(clientKey);
                try {
                    fixedOutboundSocket.send(new DatagramPacket(payload, payload.length));
                } catch (IOException e) {
                    System.err.println("[" + config.getKey() + "] UDP relay forward to destination failed: " + e.getMessage());
                }
            } else {
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
            }

            mirrorSender.send(config.getMirror().getHost(), config.getMirror().getPort(), payload, config.getKey());
        }
    }

    private void fixedReplyPumpLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running && !fixedOutboundSocket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                fixedOutboundSocket.receive(packet);
            } catch (IOException e) {
                if (!fixedOutboundSocket.isClosed()) {
                    System.err.println("[" + config.getKey() + "] UDP relay reply receive error: " + e.getMessage());
                }
                return;
            }

            byte[] replyBytes = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), replyBytes, 0, packet.getLength());

            ClientKey producer = lastProducer.get();
            if (producer != null) {
                try {
                    listenSocket.send(new DatagramPacket(replyBytes, replyBytes.length, producer.address(), producer.port()));
                } catch (IOException e) {
                    System.err.println("[" + config.getKey() + "] UDP relay reply forward to producer failed: " + e.getMessage());
                }
            }

            mirrorSender.send(config.getMirror().getHost(), config.getMirror().getPort(), replyBytes, config.getKey());
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
        if (fixedOutboundSocket != null) {
            fixedOutboundSocket.close();
        }
        if (fixedReplyPumpThread != null) {
            fixedReplyPumpThread.interrupt();
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
