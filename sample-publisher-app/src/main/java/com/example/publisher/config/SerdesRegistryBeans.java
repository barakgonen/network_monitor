package com.example.publisher.config;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import com.example.schemacore.binaryserdes.MessageType;
import com.example.schemacore.binaryserdes.Protocol;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
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
 * Per-interface {@link MessageType} lists for every serdes-backed UDP/TCP interface, keyed by
 * {@link InterfaceConfig#getKey()} - needed for field-description purposes ({@code
 * MessageDefinition}, which {@code interfaceMessageDefinitionRegistries} already gives us via
 * {@link com.example.monitor.schema.MessageSchemaWiringConfig}, doesn't expose the underlying
 * {@link MessageType}'s field tree, only encode/decode). Reparses each {@code serdesFile} once at
 * startup - a little redundant with that class's own private parsing, but small and startup-only.
 */
@Configuration
public class SerdesRegistryBeans {

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
