package com.example.monitor.rest;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestSwaggerLoaderTest {

    private final RestSwaggerLoader loader = new RestSwaggerLoader();

    @Test
    void loadResolved_withValidSpec_returnsFullyResolvedOpenApiModel() {
        OpenAPI openApi = loader.loadResolved(Path.of("src/test/resources/rest/sample-openapi.yml"));

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Test Items API");
        assertThat(openApi.getPaths()).containsKeys("/items/{itemId}", "/items");
    }

    @Test
    void loadResolved_withNonExistentFile_failsFast() {
        assertThatThrownBy(() -> loader.loadResolved(Path.of("src/test/resources/rest/does-not-exist.yml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse OpenAPI file");
    }

    @Test
    void loadResolved_withMalformedYaml_failsFast(@TempDir Path tempDir) throws IOException {
        Path malformed = tempDir.resolve("malformed.yml");
        Files.writeString(malformed, "this: [is not, valid: openapi ::: at all");

        assertThatThrownBy(() -> loader.loadResolved(malformed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadResolved_withValidYamlButNotAnOpenApiDocument_failsFast(@TempDir Path tempDir) throws IOException {
        Path notOpenApi = tempDir.resolve("not-openapi.yml");
        Files.writeString(notOpenApi, "foo: bar\nbaz: 42\n");

        assertThatThrownBy(() -> loader.loadResolved(notOpenApi))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
