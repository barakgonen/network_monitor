package com.example.serdesgenerator;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class GeneratorManifestLoader {

    public GeneratorManifest load(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Manifest file does not exist: " + path);
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(GeneratorManifest.class, loaderOptions));

        try (InputStream inputStream = Files.newInputStream(path)) {
            GeneratorManifest manifest = yaml.load(inputStream);
            validate(manifest, path);
            return manifest;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read manifest file: " + path, e);
        }
    }

    private void validate(GeneratorManifest manifest, Path path) {
        if (manifest == null || manifest.getMessages() == null || manifest.getMessages().isEmpty()) {
            throw new IllegalArgumentException("Manifest must define at least one message: " + path);
        }

        for (int i = 0; i < manifest.getMessages().size(); i++) {
            GeneratorManifestEntry entry = manifest.getMessages().get(i);

            if (entry == null) {
                throw new IllegalArgumentException("messages[" + i + "] is null in " + path);
            }
            if (entry.getClassName() == null || entry.getClassName().isBlank()) {
                throw new IllegalArgumentException("messages[" + i + "].className is required in " + path);
            }
        }
    }
}
