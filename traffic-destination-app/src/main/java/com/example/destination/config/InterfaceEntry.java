package com.example.destination.config;

public class InterfaceEntry {
    private String key;
    private String protocol;
    private Integer port;
    private ReplyMode replyMode = ReplyMode.NONE;
    private Integer replyPort;
    private String mode = "SERVER";
    private String host;

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

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public ReplyMode getReplyMode() {
        return replyMode;
    }

    public void setReplyMode(ReplyMode replyMode) {
        this.replyMode = replyMode;
    }

    /**
     * UDP only: overrides the port replies are sent to. Unset (default) replies go back to
     * whichever port the request's sender used (typically an ephemeral one); set, replies always
     * go to this fixed port on the sender's host instead - needed so a fixed-listening peer (e.g.
     * traffic-proxy-app's UdpRelay bound to a matching fixed replyPort) can reliably receive
     * replies without depending on an ephemeral source port surviving.
     */
    public Integer getReplyPort() {
        return replyPort;
    }

    public void setReplyPort(Integer replyPort) {
        this.replyPort = replyPort;
    }

    /**
     * TCP only. "SERVER" (default): bind {@code port} and accept connections in. "CLIENT":
     * connect out to {@code host}:{@code port} instead, via a background reconnect loop -
     * needed when the peer this interface talks to (e.g. traffic-proxy-app's TcpRelay in
     * reverse mode) is itself listening rather than connecting in.
     */
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    /** TCP only, mode=CLIENT only: the host to connect out to. */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}
