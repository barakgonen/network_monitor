package com.example.destination.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class DestinationConfigLoader {

    private static final Set<String> VALID_PROTOCOLS = Set.of("UDP", "TCP", "REST");

    public DestinationConfig load(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Destination config file does not exist: " + path);
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(DestinationConfig.class, loaderOptions));

        try (InputStream inputStream = Files.newInputStream(path)) {
            DestinationConfig config = yaml.load(inputStream);
            validate(config);
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read destination config file: " + path, e);
        }
    }

    private void validate(DestinationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Destination config file is empty");
        }

        if (config.getInterfaces() == null || config.getInterfaces().isEmpty()) {
            throw new IllegalArgumentException("Destination config must define at least one interface");
        }

        for (int i = 0; i < config.getInterfaces().size(); i++) {
            InterfaceEntry entry = config.getInterfaces().get(i);

            if (entry == null) {
                throw new IllegalArgumentException("interfaces[" + i + "] is null");
            }

            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("interfaces[" + i + "].key is required");
            }

            if (entry.getProtocol() == null || !VALID_PROTOCOLS.contains(entry.getProtocol().toUpperCase())) {
                throw new IllegalArgumentException(
                        "interfaces[" + i + "].protocol must be one of " + VALID_PROTOCOLS + ", was: " + entry.getProtocol());
            }

            if (entry.getPort() == null || entry.getPort() <= 0 || entry.getPort() > 65535) {
                throw new IllegalArgumentException("interfaces[" + i + "].port is invalid: " + entry.getPort());
            }
        }
    }
}
