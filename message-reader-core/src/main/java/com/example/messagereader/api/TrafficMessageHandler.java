package com.example.messagereader.api;

@FunctionalInterface
public interface TrafficMessageHandler {
    void onMessage(ParsedTrafficMessage message);
}
