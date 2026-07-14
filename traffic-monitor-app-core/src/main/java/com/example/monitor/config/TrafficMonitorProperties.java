package com.example.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic")
public class TrafficMonitorProperties {
    private Udp udp = new Udp();
    private Store store = new Store();

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
}
