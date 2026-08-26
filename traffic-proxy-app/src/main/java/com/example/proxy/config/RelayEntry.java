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
    private Integer replyPort;

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

    /**
     * UDP only. Unset (default): each producer gets its own ephemeral outbound socket, keyed by
     * a NAT table so multiple concurrent producers are told apart. Set: the relay instead binds
     * a single shared outbound socket to this fixed local port for its whole lifetime and always
     * relays replies to whichever producer sent the most recent request ("last producer wins",
     * no NAT table) - needed when the destination itself replies to a fixed configured port
     * rather than to the request's actual source port (see traffic-destination-app's matching
     * InterfaceEntry.replyPort). Trades concurrent-producer isolation for a predictable reply
     * path; fine at this project's single-producer-per-interface demo scale.
     */
    public Integer getReplyPort() {
        return replyPort;
    }

    public void setReplyPort(Integer replyPort) {
        this.replyPort = replyPort;
    }
}
