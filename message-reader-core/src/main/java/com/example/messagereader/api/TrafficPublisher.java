package com.example.messagereader.api;

public interface TrafficPublisher {
    void publish(PublishTarget target, byte[] payload);
}
