package com.example.publisher.sample;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps {@link SavedSample}s in memory, keyed by name, mirrored to a single JSON file on disk so
 * they survive an app restart. This app has no database (see CLAUDE.md's "Publishing lives in
 * sample-publisher-app, not here") - a whole-file rewrite on every mutation is simple and more
 * than fast enough for a hand-curated list of samples.
 */
@Service
public class SampleStore {

    private final ObjectMapper objectMapper;
    private final Path file;
    private final Map<String, SavedSample> samples = new LinkedHashMap<>();

    public SampleStore(
            ObjectMapper objectMapper,
            @Value("${publisher.samples.file:config/publisher-samples.json}") String filePath
    ) {
        this.objectMapper = objectMapper;
        this.file = Path.of(filePath);
        load();
    }

    private synchronized void load() {
        if (!Files.exists(file)) {
            return;
        }

        try {
            List<SavedSample> loaded = objectMapper.readValue(
                    Files.readString(file), new TypeReference<List<SavedSample>>() {
                    });
            for (SavedSample sample : loaded) {
                samples.put(sample.name(), sample);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load saved samples from " + file, e);
        }
    }

    public synchronized List<SavedSample> list() {
        return List.copyOf(samples.values());
    }

    public synchronized SavedSample save(SavedSample sample) {
        samples.put(sample.name(), sample);
        persist();
        return sample;
    }

    public synchronized void delete(String name) {
        samples.remove(name);
        persist();
    }

    public synchronized List<SavedSample> importAll(List<SavedSample> imported) {
        for (SavedSample sample : imported) {
            samples.put(sample.name(), sample);
        }
        persist();
        return list();
    }

    private void persist() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, objectMapper.writeValueAsString(List.copyOf(samples.values())));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist saved samples to " + file, e);
        }
    }
}
