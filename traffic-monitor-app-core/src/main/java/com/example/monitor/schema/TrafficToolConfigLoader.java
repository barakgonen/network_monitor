package com.example.monitor.schema;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TrafficToolConfigLoader {

    public TrafficToolConfig load() {
        String configPath = System.getenv().getOrDefault("TRAFFIC_TOOL_CONFIG", "config/traffic-tool.yml");
        return load(Paths.get(configPath));
    }

    public TrafficToolConfig load(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Traffic tool config file does not exist: " + path);
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(TrafficToolConfig.class, loaderOptions));

        try (InputStream inputStream = Files.newInputStream(path)) {
            TrafficToolConfig config = yaml.load(inputStream);
            validate(config, path);
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read traffic tool config file: " + path, e);
        }
    }

    private void validate(TrafficToolConfig config, Path path) {
        if (config == null || config.getInterfaces() == null || config.getInterfaces().isEmpty()) {
            throw new IllegalArgumentException("Traffic tool config must define at least one interface: " + path);
        }

        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            if (!interfaceConfig.hasDedicatedPort()) {
                throw new IllegalArgumentException(interfaceContext(interfaceConfig) + " must define a port");
            }

            InterfaceModeValidator.validate(
                    interfaceConfig.getMode(),
                    interfaceConfig.getProtocol(),
                    interfaceConfig.getHost(),
                    interfaceContext(interfaceConfig));

            if ("REST".equalsIgnoreCase(interfaceConfig.getProtocol())) {
                validateRestInterface(interfaceConfig);
                continue;
            }

            if (interfaceConfig.getMessages() == null || interfaceConfig.getMessages().isEmpty()) {
                throw new IllegalArgumentException(interfaceContext(interfaceConfig) + " must define at least one message");
            }

            for (MessageConfig message : interfaceConfig.getMessages()) {
                boolean hasDefinitionClass = hasText(message.getDefinitionClass());
                boolean hasMessageClass = hasText(message.getMessageClass());
                String messageContext = interfaceContext(interfaceConfig) + ".messages[type=" + message.getType() + "]";

                if (!hasDefinitionClass && !hasMessageClass) {
                    throw new IllegalArgumentException(
                            messageContext + " must define either definitionClass or messageClass");
                }

                if (hasMessageClass && message.getOpcode() == null) {
                    throw new IllegalArgumentException(messageContext + " defines messageClass but is missing opcode");
                }
            }
        }
    }

    /**
     * REST interfaces have no {@code messages:} list at all - operations are auto-discovered from
     * {@code swaggerFile} at startup ({@code RestSchemaWiringConfig}) rather than hand-declared, so
     * the only thing to validate here is that a swagger file was actually configured and exists.
     */
    private void validateRestInterface(InterfaceConfig interfaceConfig) {
        if (!hasText(interfaceConfig.getSwaggerFile())) {
            throw new IllegalArgumentException(interfaceContext(interfaceConfig) + " has protocol=REST but no swaggerFile");
        }

        Path swaggerPath = Paths.get(interfaceConfig.getSwaggerFile());
        if (!Files.exists(swaggerPath)) {
            throw new IllegalArgumentException(
                    interfaceContext(interfaceConfig) + " swaggerFile does not exist: " + swaggerPath);
        }
    }

    private String interfaceContext(InterfaceConfig interfaceConfig) {
        return "interfaces[key=" + interfaceConfig.getKey() + "]";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
