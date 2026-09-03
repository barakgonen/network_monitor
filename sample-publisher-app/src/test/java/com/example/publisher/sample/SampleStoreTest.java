package com.example.publisher.sample;

import com.example.publisher.send.SendRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SampleStoreTest {

    private static SavedSample oneShot(String name) {
        return new SavedSample(name, new SendRequest("fruit", "Orange", "localhost", 5001, "UDP", Map.of("sourceFarm", "x")), null, null);
    }

    private static SavedSample periodic(String name) {
        return new SavedSample(name, new SendRequest("fruit", "Orange", "localhost", 5001, "UDP", Map.of()), 5, "SECOND");
    }

    @Test
    void list_startsEmptyWhenFileDoesNotExist(@TempDir Path dir) {
        SampleStore store = new SampleStore(new ObjectMapper(), dir.resolve("samples.json").toString());

        assertThat(store.list()).isEmpty();
    }

    @Test
    void save_addsSampleAndPersistsToFile(@TempDir Path dir) {
        Path file = dir.resolve("samples.json");
        SampleStore store = new SampleStore(new ObjectMapper(), file.toString());

        SavedSample saved = store.save(oneShot("sample-1"));

        assertThat(saved.name()).isEqualTo("sample-1");
        assertThat(store.list()).extracting(SavedSample::name).containsExactly("sample-1");
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void save_withSameNameOverwritesExisting(@TempDir Path dir) {
        SampleStore store = new SampleStore(new ObjectMapper(), dir.resolve("samples.json").toString());

        store.save(oneShot("sample-1"));
        store.save(periodic("sample-1"));

        assertThat(store.list()).hasSize(1);
        assertThat(store.list().get(0).eventsPerTimeUnit()).isEqualTo(5);
    }

    @Test
    void delete_removesSampleAndPersists(@TempDir Path dir) {
        SampleStore store = new SampleStore(new ObjectMapper(), dir.resolve("samples.json").toString());
        store.save(oneShot("sample-1"));

        store.delete("sample-1");

        assertThat(store.list()).isEmpty();
    }

    @Test
    void importAll_upsertsEachSampleByName(@TempDir Path dir) {
        SampleStore store = new SampleStore(new ObjectMapper(), dir.resolve("samples.json").toString());
        store.save(oneShot("keep-me"));

        List<SavedSample> result = store.importAll(List.of(periodic("keep-me"), oneShot("new-one")));

        assertThat(result).extracting(SavedSample::name).containsExactlyInAnyOrder("keep-me", "new-one");
        assertThat(result).filteredOn(s -> s.name().equals("keep-me"))
                .extracting(SavedSample::eventsPerTimeUnit).containsExactly(5);
    }

    @Test
    void newStoreInstance_reloadsPersistedSamplesFromFile(@TempDir Path dir) {
        Path file = dir.resolve("samples.json");
        ObjectMapper objectMapper = new ObjectMapper();
        new SampleStore(objectMapper, file.toString()).save(periodic("sample-1"));

        SampleStore reloaded = new SampleStore(objectMapper, file.toString());

        assertThat(reloaded.list()).extracting(SavedSample::name).containsExactly("sample-1");
        assertThat(reloaded.list().get(0).timeUnit()).isEqualTo("SECOND");
    }
}
