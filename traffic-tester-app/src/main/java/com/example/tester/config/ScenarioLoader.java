package com.example.tester.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScenarioLoader {
    public TesterScenario load(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Scenario file does not exist: " + path);
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(TesterScenario.class, loaderOptions));

        try (InputStream inputStream = Files.newInputStream(path)) {
            TesterScenario scenario = yaml.load(inputStream);
            validate(scenario);
            return scenario;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read scenario file: " + path, e);
        }
    }

    private void validate(TesterScenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("Scenario file is empty");
        }
        if (scenario.getUdp() == null) {
            throw new IllegalArgumentException("Missing required section: udp");
        }
        if (scenario.getUdp().getHost() == null || scenario.getUdp().getHost().isBlank()) {
            throw new IllegalArgumentException("Missing required field: udp.host");
        }
        if (scenario.getUdp().getPort() <= 0 || scenario.getUdp().getPort() > 65535) {
            throw new IllegalArgumentException("Invalid UDP port: " + scenario.getUdp().getPort());
        }
        if (scenario.getPayload() == null) {
            throw new IllegalArgumentException("Missing required section: payload");
        }
        if (scenario.getRepeat() <= 0) {
            throw new IllegalArgumentException("repeat must be greater than 0");
        }
        if (scenario.getIntervalMillis() < 0) {
            throw new IllegalArgumentException("intervalMillis must be zero or greater");
        }
    }
}
