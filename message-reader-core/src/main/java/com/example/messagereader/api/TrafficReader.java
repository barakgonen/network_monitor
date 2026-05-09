package com.example.messagereader.api;

public interface TrafficReader extends AutoCloseable {
    void start();

    void stop();

    boolean isRunning();

    @Override
    default void close() {
        stop();
    }
}
