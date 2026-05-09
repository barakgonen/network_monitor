package com.example.tester.reader;

import com.example.messagereader.DefaultTrafficReaderFactory;
import com.example.messagereader.api.ParsedTrafficMessage;
import com.example.messagereader.api.TrafficInterfaceDefinition;
import com.example.messagereader.api.TrafficReader;
import com.example.messagereader.api.TrafficReaderFactory;
import com.example.tester.config.ReaderConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TesterTrafficReaderManager implements AutoCloseable {
    private final ReaderConfig readerConfig;
    private final TrafficReaderFactory trafficReaderFactory = new DefaultTrafficReaderFactory();
    private final List<TrafficReader> readers = new ArrayList<>();

    public TesterTrafficReaderManager(ReaderConfig readerConfig) {
        this.readerConfig = readerConfig;
    }

    public void start() {
        if (readerConfig == null || !readerConfig.isEnabled()) {
            System.out.println("Generic traffic reader is disabled");
            return;
        }

        if (readerConfig.getInterfaces() == null || readerConfig.getInterfaces().isEmpty()) {
            System.out.println("Generic traffic reader enabled, but no reader.interfaces were configured");
            return;
        }

        for (TrafficInterfaceDefinition definition : readerConfig.getInterfaces()) {
            TrafficReader reader = trafficReaderFactory.createReader(
                    definition,
                    readerConfig.getBufferSizeBytes(),
                    this::handleParsedMessage
            );

            readers.add(reader);
            reader.start();

            System.out.println("Started generic tester reader: "
                    + definition.getName()
                    + " "
                    + definition.getProtocol()
                    + ":"
                    + definition.getPort());
        }
    }

    public void awaitConfiguredDuration() throws InterruptedException {
        if (readerConfig == null || !readerConfig.isEnabled()) {
            return;
        }

        Instant deadline = Instant.now().plus(Duration.ofSeconds(readerConfig.getDurationSeconds()));

        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(250);
        }
    }

    private void handleParsedMessage(ParsedTrafficMessage message) {
        System.out.println();
        System.out.println("=== GENERIC TRAFFIC MESSAGE ARRIVED TO TESTER ===");
        System.out.println("Interface: " + message.interfaceName());
        System.out.println("Protocol: " + message.rawPacket().protocol());
        System.out.println("Local port: " + message.rawPacket().localPort());
        System.out.println("Remote: " + message.rawPacket().remoteAddress());
        System.out.println("Payload bytes: " + message.rawPacket().payloadSizeBytes());
        System.out.println("Parsed: " + message.parsed());
        System.out.println("Opcode: " + message.opcode());
        System.out.println("Message: " + message.messageName());
        System.out.println("Message class: " + message.messageClassName());

        var v = message.parsedInstance();

        if (message.parseError() != null) {
            System.out.println("Parse error: " + message.parseError());
        }

        System.out.println("Header fields: " + message.headerFields());
        System.out.println("Body fields: " + message.bodyFields());
        System.out.println("================================================");
        System.out.println();
    }

    @Override
    public void close() {
        for (TrafficReader reader : readers) {
            try {
                reader.stop();
            } catch (Exception ignored) {
            }
        }

        readers.clear();
        System.out.println("Generic tester readers stopped");
    }
}
