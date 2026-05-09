package com.example.messagereader.transport;

public interface TransportSubscriber extends AutoCloseable {
    void start();

    void stop();

    boolean isRunning();

    @Override
    default void close() {
        stop();
    }
}
