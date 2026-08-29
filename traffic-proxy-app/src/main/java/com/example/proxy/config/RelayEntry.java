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
    private String listenMode = "SERVER";
    private String destinationMode = "CLIENT";
    private long listenConnectTimeoutMillis = 3_000L;
    private long listenReconnectDelayMillis = 2_000L;

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

    /**
     * TCP only. "SERVER" (default): the relay binds {@code listen} and accepts producer
     * connections in. "CLIENT": the relay instead actively connects out to {@code listen}
     * (which in this mode names the producer's own listening server address) via a background
     * reconnect loop - needed when the producer (e.g. traffic-tester-app) is itself a TCP server
     * rather than a client.
     */
    public String getListenMode() {
        return listenMode;
    }

    public void setListenMode(String listenMode) {
        this.listenMode = listenMode;
    }

    /**
     * TCP only. "CLIENT" (default): the relay connects out to {@code destination}. "SERVER":
     * the relay instead binds {@code destination} and accepts a connection from the destination
     * side in - needed when the destination (e.g. traffic-destination-app) is itself a TCP
     * client rather than a server. Exactly one of {@code listenMode}/{@code destinationMode}
     * must be CLIENT and the other SERVER; both-SERVER and both-CLIENT are not supported.
     */
    public String getDestinationMode() {
        return destinationMode;
    }

    public void setDestinationMode(String destinationMode) {
        this.destinationMode = destinationMode;
    }

    /** TCP only, listenMode=CLIENT only: per-attempt connect timeout when dialing the producer. */
    public long getListenConnectTimeoutMillis() {
        return listenConnectTimeoutMillis;
    }

    public void setListenConnectTimeoutMillis(long listenConnectTimeoutMillis) {
        this.listenConnectTimeoutMillis = listenConnectTimeoutMillis;
    }

    /** TCP only, listenMode=CLIENT only: delay between reconnect attempts to the producer. */
    public long getListenReconnectDelayMillis() {
        return listenReconnectDelayMillis;
    }

    public void setListenReconnectDelayMillis(long listenReconnectDelayMillis) {
        this.listenReconnectDelayMillis = listenReconnectDelayMillis;
    }
}
