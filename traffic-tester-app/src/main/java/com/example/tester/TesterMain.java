package com.example.tester;

import com.example.schemas.BaseStruct;
import com.example.schemas.rada.messages.RadaExtendedStatus;
import com.example.schemas.rada.messages.RadaExtendedStatusMrs;
import com.example.schemas.rada.messages.RadaTracksExtended;
import com.example.schemautils.StructSizeCalculator;
import com.example.tester.config.PayloadConfig;
import com.example.tester.config.ScenarioLoader;
import com.example.tester.config.TesterScenario;
import com.example.tester.config.UdpListenerConfig;
import com.example.tester.payload.GenericPayloadFactory;
import com.example.tester.reader.TesterTrafficReaderManager;
import com.example.tester.udp.UdpListener;
import com.example.tester.udp.UdpPublisher;
import org.instancio.Instancio;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class TesterMain {
    public static void main(String[] args) throws Exception {
        String configPath = System.getenv().getOrDefault("TRAFFIC_TESTER_CONFIG", "./config/tester-scenario.yml");

        TesterScenario scenario = new ScenarioLoader().load(Path.of(configPath));
        List<PayloadConfig> messages = scenario.effectiveMessages();

        UdpListener listener = null;

        GenericPayloadFactory payloadFactory = new GenericPayloadFactory();
        UdpPublisher udpPublisher = new UdpPublisher();
        TesterTrafficReaderManager readerManager = null;

        System.out.println("Traffic Tester App started");
        System.out.println("Scenario: " + configPath);
        System.out.println("Default UDP target: " + scenario.getUdp().getHost() + ":" + scenario.getUdp().getPort());
        System.out.println("Messages per iteration: " + messages.size());
        System.out.println("Repeat: " + scenario.getRepeat());
        if (scenario.getReader() != null && scenario.getReader().isEnabled()) {
            readerManager = new TesterTrafficReaderManager(scenario.getReader());
            readerManager.start();

            System.out.println("Tester generic reader will run for "
                    + scenario.getReader().getDurationSeconds()
                    + " seconds");
        }

        UdpListenerConfig listenerConfig = scenario.getListener();

        int totalSent = 1;

        for (int iteration = 1; iteration <= scenario.getRepeat(); iteration++) {
            System.out.println("Starting iteration " + iteration + "/" + scenario.getRepeat());
            for (int messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
                PayloadConfig messageConfig = messages.get(messageIndex);
                String host = resolveHost(scenario, messageConfig);
                int port = resolvePort(scenario, messageConfig);

                var extendedStatusMessage = getExtendedStatusMsg(totalSent);
                byte[] messagePayload = getMessagePayload(extendedStatusMessage, RadaExtendedStatus.class);
                sendToPorts(udpPublisher, host, port, messagePayload, 3);
                totalSent++;

                var extendedStatusMrsMsg = getExtendedStatusMrsMsg(totalSent);
                messagePayload = getMessagePayload(extendedStatusMrsMsg, RadaExtendedStatusMrs.class);
                sendToPorts(udpPublisher, host, port, messagePayload, 3);
                totalSent++;

                var extendedTrackMessage = getTracksExtendedMsg(totalSent);
                messagePayload = getMessagePayload(extendedTrackMessage, RadaTracksExtended.class);
                sendToPorts(udpPublisher, host, port, messagePayload, 3);
                totalSent++;
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

    private static void sendToPorts(UdpPublisher udpPublisher, String host, int port, byte[] messagePayload, int numberOfClients) {
        for (int i = 0; i < numberOfClients; i++) {
            udpPublisher.send(host, port + i, messagePayload);
        }
    }

    private static RadaExtendedStatus getExtendedStatusMsg(int totalSent) {
        var radaExtendedStatusMsg = Instancio.create(RadaExtendedStatus.class);
        radaExtendedStatusMsg.getHeader().setMsgCounter(totalSent);
        radaExtendedStatusMsg.getHeader().setMsgType(1);

        return radaExtendedStatusMsg;
    }

    private static RadaExtendedStatusMrs getExtendedStatusMrsMsg(int totalSent) {
        var radaExtendedStatusMrs = Instancio.create(RadaExtendedStatusMrs.class);
        radaExtendedStatusMrs.getHeader().setMsgCounter(totalSent);
        radaExtendedStatusMrs.getHeader().setMsgType(2);

        return radaExtendedStatusMrs;
    }

    private static RadaTracksExtended getTracksExtendedMsg(int totalSent) {
        var radaTracksExtended = Instancio.create(RadaTracksExtended.class);
        radaTracksExtended.getHeader().setMsgCounter(totalSent);
        radaTracksExtended.getHeader().setMsgType(4);

        return radaTracksExtended;
    }

    private static byte[] getMessagePayload(BaseStruct message, Class<? extends BaseStruct> clazz) {
        int size = StructSizeCalculator.calculateStructSize(clazz);
        ByteBuffer buffer = ByteBuffer.allocate(size);
        message.toByteArray(buffer);
        byte[] payload = buffer.array();
        System.out.println("message type is: " + message.getClass() + ", payload length is: " + payload.length);
        return payload;
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
}
