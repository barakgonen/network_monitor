package com.example.tester;

import com.example.schemas.rada.messages.RadaExtendedStatus;
import com.example.schemas.rada.messages.RadaExtendedStatusMrs;
import com.example.schemas.rada.messages.RadaTracksExtended;
import com.example.schemas.rada.struct.RadaHeader;
import com.example.tester.config.PayloadConfig;
import com.example.tester.config.ScenarioLoader;
import com.example.tester.config.TesterScenario;
import com.example.tester.config.UdpListenerConfig;
import com.example.tester.payload.PayloadFactory;
import com.example.tester.udp.UdpListener;
import com.example.tester.udp.UdpPublisher;
import org.instancio.Instancio;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.instancio.internal.util.ReflectionUtils.getFieldValue;

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

        int totalSent = 1;

        for (int iteration = 1; iteration <= scenario.getRepeat(); iteration++) {
            System.out.println("Starting iteration " + iteration + "/" + scenario.getRepeat());
            for (int messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
                PayloadConfig messageConfig = messages.get(messageIndex);
                String host = resolveHost(scenario, messageConfig);
                int port = resolvePort(scenario, messageConfig);
//
                var radaExtendedStatusMsg = Instancio.create(RadaExtendedStatus.class);
                radaExtendedStatusMsg.getHeader().setMsgCounter(totalSent);
                int size = calculateSize(radaExtendedStatusMsg);
                ByteBuffer buffer = ByteBuffer.allocate(size);
                radaExtendedStatusMsg.toByteArray(buffer);
                byte[] payload = buffer.array();

                udpPublisher.send(host, port, payload);
                totalSent++;

                var radaExtendedStatusMrsMsg = Instancio.create(RadaExtendedStatusMrs.class);
                radaExtendedStatusMrsMsg.getHeader().setMsgCounter(totalSent);
                int sizeradaExtendedStatusMrsMsg = calculateSize(radaExtendedStatusMrsMsg);
                ByteBuffer bufferradaExtendedStatusMrsMsg = ByteBuffer.allocate(sizeradaExtendedStatusMrsMsg);
                radaExtendedStatusMrsMsg.toByteArray(bufferradaExtendedStatusMrsMsg);
                byte[] payloadradaExtendedStatusMrsMsg = bufferradaExtendedStatusMrsMsg.array();

                udpPublisher.send(host, port, payloadradaExtendedStatusMrsMsg);
                totalSent++;

                var radaTracksExtendedMsg = Instancio.create(RadaTracksExtended.class);
                radaTracksExtendedMsg.getHeader().setMsgCounter(totalSent);
                int sizeRadaTracksExtended = calculateSize(radaTracksExtendedMsg);
                ByteBuffer bufferradaTracksExtendedMsg = ByteBuffer.allocate(sizeRadaTracksExtended);
                radaTracksExtendedMsg.toByteArray(bufferradaTracksExtendedMsg);
                byte[] payloadRadaTracksExtended = bufferradaTracksExtendedMsg.array();

                udpPublisher.send(host, port, payloadRadaTracksExtended);
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
    public static int calculateSize(Object obj) {
        try {
            return calculateSizeInternal(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to calculate binary size", e);
        }
    }

    private static int calculateSizeInternal(Object obj) throws IllegalAccessException {
        if (obj == null) {
            return 0;
        }

        Class<?> clazz = obj.getClass();

        if (isUnsupportedJdkType(clazz)) {
            throw new IllegalArgumentException(
                    "Unsupported type for binary size calculation: " + clazz.getName()
            );
        }

        int size = 0;

        for (Field field : clazz.getDeclaredFields()) {

            if (Modifier.isStatic(field.getModifiers())
                    || Modifier.isTransient(field.getModifiers())
                    || field.isSynthetic()) {
                continue;
            }

            Class<?> type = field.getType();

            if (isUnsupportedJdkType(type)) {
                throw new IllegalArgumentException(
                        "Unsupported field type: " + type.getName()
                                + ", field: " + clazz.getSimpleName() + "." + field.getName()
                );
            }

            field.setAccessible(true);
            Object value = field.get(obj);

            size += calculateFieldSize(type, value, field);
        }

        return size;
    }

    private static int calculateFieldSize(Class<?> type, Object value, Field field)
            throws IllegalAccessException {

        if (type == byte.class) {
            return Byte.BYTES;
        }

        if (type == short.class) {
            return Short.BYTES;
        }

        if (type == int.class) {
            return Integer.BYTES;
        }

        if (type == long.class) {
            return Long.BYTES;
        }

        if (type == float.class) {
            return Float.BYTES;
        }

        if (type == double.class) {
            return Double.BYTES;
        }

        if (type == boolean.class) {
            return Byte.BYTES;
        }

        if (type.isEnum()) {
            return Integer.BYTES; // change if you serialize enum differently
        }

        if (type.isArray()) {
            return calculateArraySize(value);
        }

        return calculateSizeInternal(value);
    }

    private static int calculateArraySize(Object array) throws IllegalAccessException {
        if (array == null) {
            return 0;
        }

        int length = Array.getLength(array);
        Class<?> componentType = array.getClass().getComponentType();

        if (componentType == byte.class) {
            return length * Byte.BYTES;
        }

        if (componentType == short.class) {
            return length * Short.BYTES;
        }

        if (componentType == int.class) {
            return length * Integer.BYTES;
        }

        if (componentType == long.class) {
            return length * Long.BYTES;
        }

        if (componentType == float.class) {
            return length * Float.BYTES;
        }

        if (componentType == double.class) {
            return length * Double.BYTES;
        }

        if (componentType == boolean.class) {
            return length * Byte.BYTES;
        }

        int size = 0;

        for (int i = 0; i < length; i++) {
            Object element = Array.get(array, i);
            size += calculateSizeInternal(element);
        }

        return size;
    }

    private static boolean isUnsupportedJdkType(Class<?> type) {
        if (type.isPrimitive()) {
            return false;
        }

        if (type.isArray()) {
            return false;
        }

        if (type.isEnum()) {
            return false;
        }

        String name = type.getName();

        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.");
    }
}
