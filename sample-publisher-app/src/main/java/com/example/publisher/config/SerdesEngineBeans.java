package com.example.publisher.config;

import com.example.binaryserdes.MessageType;
import com.example.binaryserdes.Protocol;
import com.example.binaryserdes.ProtocolIn;
import com.example.binaryserdes.ProtocolOut;
import com.example.binaryserdes.config.ProtocolConfig;
import com.example.trafficconfig.InterfaceConfig;
import com.example.trafficconfig.TrafficToolConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-interface {@link MessageType} lists and {@link ProtocolOut} encoders for every serdes-backed
 * UDP/TCP interface, keyed by {@link InterfaceConfig#getKey()} - both built from the same parsed
 * {@link ProtocolConfig} in one pass per interface. {@link MessageType} lists describe fields for
 * the UI; {@link ProtocolOut} encodes a send request directly - this app has no access to
 * {@code MessageDefinition}/{@code SerdesMessageDefinition} (ingestion-only abstractions that
 * stayed behind in traffic-monitor-app-core), so {@link com.example.publisher.send.SendOrchestrationService}
 * works with {@link ProtocolOut} directly instead of going through that layer.
 */
@Configuration
public class SerdesEngineBeans {

    @Bean
    public Map<String, List<MessageType>> interfaceMessageTypes(TrafficToolConfig config) {
        Map<String, List<MessageType>> result = new LinkedHashMap<>();

        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            if (!interfaceConfig.hasSerdesFile()) {
                continue;
            }

            ProtocolConfig protocolConfig = loadSerdesConfig(interfaceConfig);
            ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(protocolConfig);
            result.put(interfaceConfig.getKey(), new ArrayList<>(protocolIn.getByName().values()));
        }

        return result;
    }

    @Bean
    public Map<String, ProtocolOut> interfaceProtocolOuts(TrafficToolConfig config) {
        Map<String, ProtocolOut> result = new LinkedHashMap<>();

        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            if (!interfaceConfig.hasSerdesFile()) {
                continue;
            }

            ProtocolConfig protocolConfig = loadSerdesConfig(interfaceConfig);
            result.put(interfaceConfig.getKey(), ProtocolOut.fromProtocolConfig(protocolConfig));
        }

        return result;
    }

    private ProtocolConfig loadSerdesConfig(InterfaceConfig interfaceConfig) {
        try (InputStream in = Files.newInputStream(Paths.get(interfaceConfig.getSerdesFile()))) {
            return Protocol.loadConfig(in);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read serdesFile for interface " + interfaceConfig.getKey()
                            + ": " + interfaceConfig.getSerdesFile(), e);
        }
    }
}
