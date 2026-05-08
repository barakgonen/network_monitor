package com.example.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "traffic")
public class TrafficMonitorProperties {
    private Udp udp = new Udp();
    private Store store = new Store();
    private int interfaceActiveWindowSeconds = 10;
    private List<ReflectionInterface> reflectionInterfaces = new ArrayList<>();

    public Udp getUdp() {
        return udp;
    }

    public void setUdp(Udp udp) {
        this.udp = udp;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public int getInterfaceActiveWindowSeconds() {
        return interfaceActiveWindowSeconds;
    }

    public void setInterfaceActiveWindowSeconds(int interfaceActiveWindowSeconds) {
        this.interfaceActiveWindowSeconds = interfaceActiveWindowSeconds;
    }

    public List<ReflectionInterface> getReflectionInterfaces() {
        return reflectionInterfaces;
    }

    public void setReflectionInterfaces(List<ReflectionInterface> reflectionInterfaces) {
        this.reflectionInterfaces = reflectionInterfaces;
    }

    public static class Udp {
        private boolean enabled = true;
        private int fruitPort = 5001;
        private int weatherPort = 5003;
        private int bufferSizeBytes = 65507;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getFruitPort() {
            return fruitPort;
        }

        public void setFruitPort(int fruitPort) {
            this.fruitPort = fruitPort;
        }

        public int getWeatherPort() {
            return weatherPort;
        }

        public void setWeatherPort(int weatherPort) {
            this.weatherPort = weatherPort;
        }

        public int getBufferSizeBytes() {
            return bufferSizeBytes;
        }

        public void setBufferSizeBytes(int bufferSizeBytes) {
            this.bufferSizeBytes = bufferSizeBytes;
        }
    }

    public static class Store {
        private int maxSize = 500;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }

    public static class ReflectionInterface {
        private String name;
        private boolean enabled = true;
        private String protocol = "UDP";
        private int port;
        private String headerType;
        private String opcodeFieldName;
        private String byteOrder = "BIG_ENDIAN";
        private Map<String, SupportedMessage> supportedMessages = new LinkedHashMap<>();

        /**
         * Backward compatibility only. Prefer supportedMessages map.
         */
        private List<String> potentialMessages = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getHeaderType() {
            return headerType;
        }

        public void setHeaderType(String headerType) {
            this.headerType = headerType;
        }

        public String getOpcodeFieldName() {
            return opcodeFieldName;
        }

        public void setOpcodeFieldName(String opcodeFieldName) {
            this.opcodeFieldName = opcodeFieldName;
        }

        public String getByteOrder() {
            return byteOrder;
        }

        public void setByteOrder(String byteOrder) {
            this.byteOrder = byteOrder;
        }

        public Map<String, SupportedMessage> getSupportedMessages() {
            return supportedMessages;
        }

        public void setSupportedMessages(Map<String, SupportedMessage> supportedMessages) {
            this.supportedMessages = supportedMessages;
        }

        public List<String> getPotentialMessages() {
            return potentialMessages;
        }

        public void setPotentialMessages(List<String> potentialMessages) {
            this.potentialMessages = potentialMessages;
        }
    }

    public static class SupportedMessage {
        private String messageClass;
        private String displayName;

        public String getMessageClass() {
            return messageClass;
        }

        public void setMessageClass(String messageClass) {
            this.messageClass = messageClass;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}
