package com.example.tester;

import com.example.tester.config.PayloadConfig;
import com.example.tester.config.ScenarioLoader;
import com.example.tester.config.TesterScenario;
import com.example.tester.config.UdpListenerConfig;
import com.example.tester.payload.PayloadFactory;
import com.example.tester.tcp.TcpPublisher;
import com.example.tester.udp.UdpListener;
import com.example.tester.udp.UdpPublisher;

import java.nio.file.Path;
import java.time.Duration;
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

        int totalSent = 0;

        for (int iteration = 1; iteration <= scenario.getRepeat(); iteration++) {
            System.out.println("Starting iteration " + iteration + "/" + scenario.getRepeat());

            for (int messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
                PayloadConfig messageConfig = messages.get(messageIndex);
                byte[] payload = payloadFactory.create(messageConfig);

                String host = resolveHost(scenario, messageConfig);
                int port = resolvePort(scenario, messageConfig);
                String transport = resolveTransport(messageConfig);

                if ("TCP".equals(transport)) {
                    tcpPublisher.send(host, port, payload);
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
            }

            if (iteration < scenario.getRepeat() && scenario.getIntervalMillis() > 0) {
                Thread.sleep(scenario.getIntervalMillis());
            }
        }

        System.out.println("Traffic Tester App finished sending. Total messages sent: " + totalSent);

        if (listener != null) {
            listener.await(Duration.ofSeconds(listenerConfig.getDurationSeconds()));
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
