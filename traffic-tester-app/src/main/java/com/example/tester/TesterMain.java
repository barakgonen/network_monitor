package com.example.tester;

import com.example.tester.config.PayloadConfig;
import com.example.tester.config.ScenarioLoader;
import com.example.tester.config.TesterScenario;
import com.example.tester.config.UdpListenerConfig;
import com.example.tester.payload.PayloadFactory;
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

        // TCP_SERVER-transport messages don't get "sent" from the per-iteration loop below - the
        // tester is itself the TCP server here, so each one gets its own TcpListener started up
        // front, pushing its configured payload immediately whenever a peer (e.g.
        // traffic-proxy-app's TcpRelay in reverse mode) connects in.
        long tcpServerDurationSeconds = listenerConfig != null ? listenerConfig.getDurationSeconds() : 120;
        List<TcpListener> tcpListeners = new ArrayList<>();
        for (PayloadConfig messageConfig : messages) {
            if ("TCP_SERVER".equals(resolveTransport(messageConfig))) {
                int tcpServerPort = resolvePort(scenario, messageConfig);
                byte[] payload = payloadFactory.create(messageConfig);
                TcpListener tcpListener = new TcpListener(tcpServerPort, payload);
                tcpListener.start();
                tcpListeners.add(tcpListener);

                System.out.println("Tester will serve TCP connections for " + messageConfig.getMode()
                        + " on port " + tcpServerPort + " for " + tcpServerDurationSeconds + " seconds");
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

                if ("TCP_SERVER".equals(transport)) {
                    // Already being served by a dedicated TcpListener started above.
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

        // Both the UDP listener and any TCP server listeners run their own background threads
        // already; block here for whichever duration is longest, then close them all together,
        // rather than awaiting each one sequentially (which would needlessly serialize their
        // otherwise-concurrent listen windows).
        long overallListenDurationSeconds = 0;
        if (listener != null) {
            overallListenDurationSeconds = Math.max(overallListenDurationSeconds, listenerConfig.getDurationSeconds());
        }
        if (!tcpListeners.isEmpty()) {
            overallListenDurationSeconds = Math.max(overallListenDurationSeconds, tcpServerDurationSeconds);
        }
        if (overallListenDurationSeconds > 0) {
            Thread.sleep(Duration.ofSeconds(overallListenDurationSeconds).toMillis());
        }
        if (listener != null) {
            listener.close();
        }
        for (TcpListener tcpListener : tcpListeners) {
            tcpListener.close();
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
