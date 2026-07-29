package com.example.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic")
public class TrafficMonitorProperties {
    private Udp udp = new Udp();
    private Tcp tcp = new Tcp();
    private Store store = new Store();

    public Udp getUdp() {
        return udp;
    }

    public void setUdp(Udp udp) {
        this.udp = udp;
    }

    public Tcp getTcp() {
        return tcp;
    }

    public void setTcp(Tcp tcp) {
        this.tcp = tcp;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public static class Udp {
        private int bufferSizeBytes = 65507;

        public int getBufferSizeBytes() {
            return bufferSizeBytes;
        }

        public void setBufferSizeBytes(int bufferSizeBytes) {
            this.bufferSizeBytes = bufferSizeBytes;
        }
    }

    public static class Tcp {
        private int maxBodyLengthBytes = 65507;
        private int clientReconnectDelayMs = 2000;
        private int clientConnectTimeoutMs = 3000;

        public int getMaxBodyLengthBytes() {
            return maxBodyLengthBytes;
        }

        public void setMaxBodyLengthBytes(int maxBodyLengthBytes) {
            this.maxBodyLengthBytes = maxBodyLengthBytes;
        }

        public int getClientReconnectDelayMs() {
            return clientReconnectDelayMs;
        }

        public void setClientReconnectDelayMs(int clientReconnectDelayMs) {
            this.clientReconnectDelayMs = clientReconnectDelayMs;
        }

        public int getClientConnectTimeoutMs() {
            return clientConnectTimeoutMs;
        }

        public void setClientConnectTimeoutMs(int clientConnectTimeoutMs) {
            this.clientConnectTimeoutMs = clientConnectTimeoutMs;
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
}
