package com.example.tester;

import com.example.tester.config.PayloadConfig;
import com.example.tester.config.ScenarioLoader;
import com.example.tester.config.TesterScenario;
import com.example.tester.config.UdpListenerConfig;
import com.example.tester.payload.PayloadFactory;
import com.example.tester.rest.RestListener;
import com.example.tester.rest.RestPublisher;
import com.example.tester.rest.RestSendResult;
import com.example.tester.tcp.TcpListener;
import com.example.tester.tcp.TcpPublisher;
import com.example.tester.udp.UdpListener;
import com.example.tester.udp.UdpPublisher;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public class TesterMain {
    public static void main(String[] args) throws Exception {
        String configPath = System.getenv().getOrDefault("TRAFFIC_TESTER_CONFIG", "./config/tester-scenario.yml");

        TesterScenario scenario = new ScenarioLoader().load(Path.of(configPath));
        List<PayloadConfig> messages = scenario.effectiveMessages();

        PayloadFactory payloadFactory = new PayloadFactory();
        UdpPublisher udpPublisher = new UdpPublisher();
        TcpPublisher tcpPublisher = new TcpPublisher();
        RestPublisher restPublisher = new RestPublisher();

        UdpListener listener = null;

        System.out.println("Traffic Tester App started");
        System.out.println("Scenario: " + configPath);
        System.out.println("Default UDP target: " + scenario.getUdp().getHost() + ":" + scenario.getUdp().getPort());
        System.out.println("Messages per iteration: " + messages.size());
        System.out.println("Repeat: " + scenario.getRepeat());

        UdpListenerConfig listenerConfig = scenario.getListener();

        if (listenerConfig != null && listenerConfig.isEnabled()) {
            listener = new UdpListener(listenerConfig.getPort(), listenerConfig.getBufferSizeBytes());
            listener.start();

            System.out.println("Tester will listen for UDP responses on port "
                    + listenerConfig.getPort()
                    + " for "
                    + listenerConfig.getDurationSeconds()
                    + " seconds");
        }

        // TCP_SERVER/REST_SERVER-transport messages don't get "sent" from the per-iteration loop
        // below - the tester is itself the server here, so each one gets its own listener started
        // up front. TCP_SERVER pushes its configured payload immediately whenever a peer (e.g.
        // traffic-proxy-app's TcpRelay in reverse mode) connects in; REST_SERVER can only respond
        // (HTTP has no server-push), so it just echoes whatever request arrives. Both run until
        // the process is killed (see the end of main) - the peer sending to them (e.g.
        // traffic-destination-app's periodic REST client) has no fixed lifetime either.
        List<TcpListener> tcpListeners = new ArrayList<>();
        List<RestListener> restListeners = new ArrayList<>();
        for (PayloadConfig messageConfig : messages) {
            String messageTransport = resolveTransport(messageConfig);
            if ("TCP_SERVER".equals(messageTransport)) {
                int tcpServerPort = resolvePort(scenario, messageConfig);
                byte[] payload = payloadFactory.create(messageConfig);
                TcpListener tcpListener = new TcpListener(tcpServerPort, payload);
                tcpListener.start();
                tcpListeners.add(tcpListener);

                System.out.println("Tester will serve TCP connections for " + messageConfig.getMode()
                        + " on port " + tcpServerPort + " until stopped");
            } else if ("REST_SERVER".equals(messageTransport)) {
                int restServerPort = resolvePort(scenario, messageConfig);
                RestListener restListener = new RestListener(restServerPort);
                restListener.start();
                restListeners.add(restListener);

                System.out.println("Tester will serve REST requests on port " + restServerPort + " until stopped");
            }
        }

        int totalSent = 0;
        int totalFailed = 0;

        for (int iteration = 1; iteration <= scenario.getRepeat(); iteration++) {
            System.out.println("Starting iteration " + iteration + "/" + scenario.getRepeat());

            for (int messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
                PayloadConfig messageConfig = messages.get(messageIndex);
                String host = resolveHost(scenario, messageConfig);
                int port = resolvePort(scenario, messageConfig);
                String transport = resolveTransport(messageConfig);

                if ("TCP_SERVER".equals(transport) || "REST_SERVER".equals(transport)) {
                    // Already being served by a dedicated TcpListener/RestListener started above.
                    continue;
                }

                // One message's target being unreachable (e.g. a relay intentionally disabled in
                // config) must not abort every other message in the scenario - log and move on.
                try {
                    if ("REST".equals(transport)) {
                        RestSendResult result = restPublisher.send(host, port, messageConfig);
                        totalSent++;

                        System.out.println("Sent message "
                                + (messageIndex + 1)
                                + "/"
                                + messages.size()
                                + " type="
                                + messageConfig.getMode()
                                + ", transport=REST, method="
                                + result.method()
                                + ", target="
                                + host
                                + ":"
                                + port
                                + ", status="
                                + result.statusCode()
                                + ", responseBody="
                                + result.body());
                        continue;
                    }

                    byte[] payload = payloadFactory.create(messageConfig);

                    if ("TCP".equals(transport)) {
                        tcpPublisher.send(host, port, payload);
                    } else if (listener != null) {
                        // Send from the listener's own bound socket so a reply routed back to this
                        // socket's local port (e.g. by traffic-proxy-app's UDP relay) actually reaches
                        // the listener still bound there, instead of a throwaway ephemeral socket.
                        udpPublisher.send(listener.socket(), host, port, payload);
                    } else {
                        udpPublisher.send(host, port, payload);
                    }

                    totalSent++;

                    System.out.println("Sent message "
                            + (messageIndex + 1)
                            + "/"
                            + messages.size()
                            + " type="
                            + messageConfig.getMode()
                            + ", transport="
                            + transport
                            + ", target="
                            + host
                            + ":"
                            + port
                            + ", bytes="
                            + payload.length
                            + ", hex="
                            + HexFormat.of().formatHex(payload));
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    totalFailed++;
                    System.err.println("Failed to send message "
                            + (messageIndex + 1)
                            + "/"
                            + messages.size()
                            + " type="
                            + messageConfig.getMode()
                            + ", transport="
                            + transport
                            + ", target="
                            + host
                            + ":"
                            + port
                            + ": "
                            + e.getMessage());
                }
            }

            if (iteration < scenario.getRepeat() && scenario.getIntervalMillis() > 0) {
                Thread.sleep(scenario.getIntervalMillis());
            }
        }

        System.out.println("Traffic Tester App finished sending. Total messages sent: " + totalSent
                + (totalFailed > 0 ? ", failed: " + totalFailed : ""));

        if (!tcpListeners.isEmpty() || !restListeners.isEmpty()) {
            // A TCP_SERVER/REST_SERVER listener stands in for a peer that pushes/sends traffic to
            // the tester on its own schedule (e.g. traffic-destination-app's periodic REST client
            // in a reversed-role relay, which runs forever) - there's no fixed point at which that
            // peer stops sending, so - like traffic-destination-app itself - these listeners must
            // stay up until the process is killed, not for a fixed duration tied to the (unrelated)
            // UDP reply-listening window below. Closing them early is exactly what produced the
            // "arrives at the monitor's mirror but never at the tester" symptom: once this window
            // closed, every subsequent periodic request 502'd at the proxy (destination
            // unreachable) while still being mirrored to the monitor regardless.
            if (listener != null) {
                UdpListener finalListener = listener;
                Runtime.getRuntime().addShutdownHook(new Thread(finalListener::close));
            }
            for (TcpListener tcpListener : tcpListeners) {
                Runtime.getRuntime().addShutdownHook(new Thread(tcpListener::close));
            }
            for (RestListener restListener : restListeners) {
                Runtime.getRuntime().addShutdownHook(new Thread(restListener::close));
            }

            System.out.println("Tester will keep serving TCP_SERVER/REST_SERVER connections until stopped (Ctrl+C)");
            Thread.currentThread().join();
            return;
        }

        // No server-mode listener is in play - just the UDP reply-listener (if configured),
        // which only needs to stay open for its own configured window before closing.
        if (listener != null) {
            Thread.sleep(Duration.ofSeconds(listenerConfig.getDurationSeconds()).toMillis());
            listener.close();
        }

        System.out.println("Traffic Tester App finished");
    }

    private static String resolveHost(TesterScenario scenario, PayloadConfig messageConfig) {
        if (messageConfig.getTarget() != null
                && messageConfig.getTarget().getHost() != null
                && !messageConfig.getTarget().getHost().isBlank()) {
            return messageConfig.getTarget().getHost();
        }

        return scenario.getUdp().getHost();
    }

    private static int resolvePort(TesterScenario scenario, PayloadConfig messageConfig) {
        if (messageConfig.getTarget() != null && messageConfig.getTarget().getPort() != null) {
            return messageConfig.getTarget().getPort();
        }

        return scenario.getUdp().getPort();
    }

    private static String resolveTransport(PayloadConfig messageConfig) {
        if (messageConfig.getTarget() != null
                && messageConfig.getTarget().getTransport() != null
                && !messageConfig.getTarget().getTransport().isBlank()) {
            return messageConfig.getTarget().getTransport().trim().toUpperCase(Locale.ROOT);
        }

        return "UDP";
    }
}
