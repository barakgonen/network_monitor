package com.example.proxy.config;

public class RelayEntry {
    private String key;
    private String protocol;
    private EndpointConfig listen;
    private EndpointConfig destination;
    private EndpointConfig mirror;
    private long natIdleTimeoutMillis = 120_000L;
    private long natIdleSweepIntervalMillis = 30_000L;
    private long destinationConnectTimeoutMillis = 5_000L;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public EndpointConfig getListen() {
        return listen;
    }

    public void setListen(EndpointConfig listen) {
        this.listen = listen;
    }

    public EndpointConfig getDestination() {
        return destination;
    }

    public void setDestination(EndpointConfig destination) {
        this.destination = destination;
    }

    public EndpointConfig getMirror() {
        return mirror;
    }

    public void setMirror(EndpointConfig mirror) {
        this.mirror = mirror;
    }

    public long getNatIdleTimeoutMillis() {
        return natIdleTimeoutMillis;
    }

    public void setNatIdleTimeoutMillis(long natIdleTimeoutMillis) {
        this.natIdleTimeoutMillis = natIdleTimeoutMillis;
    }

    public long getNatIdleSweepIntervalMillis() {
        return natIdleSweepIntervalMillis;
    }

    public void setNatIdleSweepIntervalMillis(long natIdleSweepIntervalMillis) {
        this.natIdleSweepIntervalMillis = natIdleSweepIntervalMillis;
    }

    public long getDestinationConnectTimeoutMillis() {
        return destinationConnectTimeoutMillis;
    }

    public void setDestinationConnectTimeoutMillis(long destinationConnectTimeoutMillis) {
        this.destinationConnectTimeoutMillis = destinationConnectTimeoutMillis;
    }
}
