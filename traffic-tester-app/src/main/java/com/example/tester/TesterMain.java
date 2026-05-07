package com.example.tester;

import com.example.schemas.rada.messages.RadaExtendedStatus;
import com.example.schemas.rada.struct.RadaHeader;
import com.example.tester.config.PayloadConfig;
import com.example.tester.config.ScenarioLoader;
import com.example.tester.config.TesterScenario;
import com.example.tester.config.UdpListenerConfig;
import com.example.tester.payload.PayloadFactory;
import com.example.tester.udp.UdpListener;
import com.example.tester.udp.UdpPublisher;
import org.instancio.Instancio;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

public class TesterMain {
    public static void main(String[] args) throws Exception {
        String configPath = System.getenv().getOrDefault("TRAFFIC_TESTER_CONFIG", "./config/tester-scenario.yml");

        TesterScenario scenario = new ScenarioLoader().load(Path.of(configPath));
        List<PayloadConfig> messages = scenario.effectiveMessages();

        PayloadFactory payloadFactory = new PayloadFactory();
        UdpPublisher udpPublisher = new UdpPublisher();

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
//                byte[] payload = payloadFactory.create(messageConfig);
                var radaExtendedStatusMsg = Instancio.create(RadaExtendedStatus.class);
                int size = calculateSize(radaExtendedStatusMsg);
                ByteBuffer buffer = ByteBuffer.allocate(size);
                byte[] payload = buffer.array();
                radaExtendedStatusMsg.toByteArray(buffer);


                String host = resolveHost(scenario, messageConfig);
                int port = resolvePort(scenario, messageConfig);

                udpPublisher.send(host, port, payload);

                totalSent++;

                System.out.println("Sent message "
                        + (messageIndex + 1)
                        + "/"
                        + messages.size()
                        + " type="
                        + messageConfig.getMode()
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

    public static int calculateSize(Object obj) throws IllegalAccessException {
        if (obj == null) {
            return 0;
        }

        int size = 0;

        for (Field field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);

            Class<?> type = field.getType();
            Object value = field.get(obj);

            if (type == int.class) {
                size += Integer.BYTES;

            } else if (type == float.class) {
                size += Float.BYTES;

            } else if (type == long.class) {
                size += Long.BYTES;

            } else if (type == byte.class) {
                size += Byte.BYTES;

            } else if (type == short.class) {
                size += Short.BYTES;

            } else if (type == double.class) {
                size += Double.BYTES;

            } else if (type == boolean.class) {
                size += Byte.BYTES; // common binary representation

            } else {
                // complex/nested object
                size += calculateSize(value);
            }
        }

        return size;
    }
}
