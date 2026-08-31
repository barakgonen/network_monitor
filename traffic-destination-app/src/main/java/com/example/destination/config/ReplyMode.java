package com.example.destination.config;

public enum ReplyMode {
    NONE,
    ECHO,
    /** Ping-specific: decode the incoming legacy-envelope Ping and reply with a real Pong. */
    PONG,
    /** Greeting-specific: decode the incoming legacy-envelope Beacon and reply with a real Greeting. */
    GREETING
}
