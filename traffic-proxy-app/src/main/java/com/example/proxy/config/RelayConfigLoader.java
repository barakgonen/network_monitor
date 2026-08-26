package com.example.proxy.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class RelayConfigLoader {

    private static final Set<String> VALID_PROTOCOLS = Set.of("UDP", "TCP", "REST");
    private static final Set<String> VALID_TCP_MODES = Set.of("SERVER", "CLIENT");

    public ProxyRelayConfig load(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Proxy relay config file does not exist: " + path);
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(ProxyRelayConfig.class, loaderOptions));

        try (InputStream inputStream = Files.newInputStream(path)) {
            ProxyRelayConfig config = yaml.load(inputStream);
            validate(config);
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read proxy relay config file: " + path, e);
        }
    }

    private void validate(ProxyRelayConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Proxy relay config file is empty");
        }

        if (config.getRelays() == null || config.getRelays().isEmpty()) {
            throw new IllegalArgumentException("Proxy relay config must define at least one relay");
        }

        Set<Integer> seenListenPorts = new HashSet<>();

        for (int i = 0; i < config.getRelays().size(); i++) {
            RelayEntry entry = config.getRelays().get(i);
            String prefix = "relays[" + i + "]";

            if (entry == null) {
                throw new IllegalArgumentException(prefix + " is null");
            }

            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException(prefix + ".key is required");
            }

            if (entry.getProtocol() == null || !VALID_PROTOCOLS.contains(entry.getProtocol().toUpperCase())) {
                throw new IllegalArgumentException(
                        prefix + ".protocol must be one of " + VALID_PROTOCOLS + ", was: " + entry.getProtocol());
            }

            validateEndpoint(entry.getListen(), prefix + ".listen");
            validateEndpoint(entry.getDestination(), prefix + ".destination");
            validateEndpoint(entry.getMirror(), prefix + ".mirror");

            int listenPort = entry.getListen().getPort();
            if (listenPort == entry.getDestination().getPort()) {
                throw new IllegalArgumentException(prefix + ".listen.port must not equal destination.port: " + listenPort);
            }
            if (listenPort == entry.getMirror().getPort()) {
                throw new IllegalArgumentException(prefix + ".listen.port must not equal mirror.port: " + listenPort);
            }

            if (!seenListenPorts.add(listenPort)) {
                throw new IllegalArgumentException("Duplicate listen.port across relay entries: " + listenPort);
            }

            if (entry.getReplyPort() != null) {
                if (!"UDP".equalsIgnoreCase(entry.getProtocol())) {
                    throw new IllegalArgumentException(
                            prefix + ".replyPort is only valid for protocol UDP, was: " + entry.getProtocol());
                }
                int replyPort = entry.getReplyPort();
                if (replyPort <= 0 || replyPort > 65535) {
                    throw new IllegalArgumentException(prefix + ".replyPort is invalid: " + replyPort);
                }
                if (replyPort == listenPort) {
                    throw new IllegalArgumentException(prefix + ".replyPort must not equal listen.port: " + replyPort);
                }
            }

            validateTcpModes(entry, prefix);
        }
    }

    private void validateTcpModes(RelayEntry entry, String prefix) {
        boolean isTcp = "TCP".equalsIgnoreCase(entry.getProtocol());
        boolean listenModeDefault = "SERVER".equalsIgnoreCase(entry.getListenMode());
        boolean destinationModeDefault = "CLIENT".equalsIgnoreCase(entry.getDestinationMode());

        if (!isTcp) {
            if (!listenModeDefault || !destinationModeDefault) {
                throw new IllegalArgumentException(
                        prefix + ".listenMode/destinationMode are only valid for protocol TCP, was: " + entry.getProtocol());
            }
            return;
        }

        if (entry.getListenMode() == null || !VALID_TCP_MODES.contains(entry.getListenMode().toUpperCase())) {
            throw new IllegalArgumentException(
                    prefix + ".listenMode must be one of " + VALID_TCP_MODES + ", was: " + entry.getListenMode());
        }
        if (entry.getDestinationMode() == null || !VALID_TCP_MODES.contains(entry.getDestinationMode().toUpperCase())) {
            throw new IllegalArgumentException(
                    prefix + ".destinationMode must be one of " + VALID_TCP_MODES + ", was: " + entry.getDestinationMode());
        }

        boolean listenIsServer = "SERVER".equalsIgnoreCase(entry.getListenMode());
        boolean destinationIsServer = "SERVER".equalsIgnoreCase(entry.getDestinationMode());
        if (listenIsServer == destinationIsServer) {
            throw new IllegalArgumentException(prefix + ": exactly one of listenMode/destinationMode must be SERVER "
                    + "and the other CLIENT (both-SERVER and both-CLIENT are not supported), was listenMode="
                    + entry.getListenMode() + ", destinationMode=" + entry.getDestinationMode());
        }
    }

    private void validateEndpoint(EndpointConfig endpoint, String fieldPrefix) {
        if (endpoint == null) {
            throw new IllegalArgumentException(fieldPrefix + " is required");
        }
        if (endpoint.getHost() == null || endpoint.getHost().isBlank()) {
            throw new IllegalArgumentException(fieldPrefix + ".host is required");
        }
        if (endpoint.getPort() == null || endpoint.getPort() <= 0 || endpoint.getPort() > 65535) {
            throw new IllegalArgumentException(fieldPrefix + ".port is invalid: " + endpoint.getPort());
        }
    }
}
