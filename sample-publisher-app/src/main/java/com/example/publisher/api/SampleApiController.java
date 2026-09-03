package com.example.publisher.api;

import com.example.publisher.sample.SampleStore;
import com.example.publisher.sample.SavedSample;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SampleApiController {

    private final SampleStore sampleStore;

    public SampleApiController(SampleStore sampleStore) {
        this.sampleStore = sampleStore;
    }

    @GetMapping("/api/samples")
    public List<SavedSample> list() {
        return sampleStore.list();
    }

    @PostMapping("/api/samples")
    public SavedSample save(@RequestBody SavedSample sample) {
        return sampleStore.save(sample);
    }

    @DeleteMapping("/api/samples/{name}")
    public ResponseEntity<Void> delete(@PathVariable("name") String name) {
        sampleStore.delete(name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/samples/export")
    public ResponseEntity<List<SavedSample>> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"publisher-samples.json\"")
                .body(sampleStore.list());
    }

    @PostMapping("/api/samples/import")
    public List<SavedSample> importAll(@RequestBody List<SavedSample> samples) {
        return sampleStore.importAll(samples);
    }
}
