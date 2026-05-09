package com.example.messagereader.api;

public interface TrafficReaderFactory {
    TrafficReader createReader(
            TrafficInterfaceDefinition definition,
            int bufferSizeBytes,
            TrafficMessageHandler handler
    );
}
