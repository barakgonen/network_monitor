package com.example.publisher.api;

import com.example.publisher.periodic.PeriodicJobDto;
import com.example.publisher.periodic.PeriodicSchedulerService;
import com.example.publisher.periodic.PeriodicStartRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PeriodicApiController {

    private final PeriodicSchedulerService scheduler;

    public PeriodicApiController(PeriodicSchedulerService scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping("/api/periodic/start")
    public PeriodicJobDto start(@RequestBody PeriodicStartRequest request) {
        return scheduler.start(request);
    }

    @PostMapping("/api/periodic/{jobId}/stop")
    public ResponseEntity<PeriodicJobDto> stop(@PathVariable("jobId") String jobId) {
        return scheduler.stop(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/periodic")
    public List<PeriodicJobDto> list() {
        return scheduler.list();
    }

    @GetMapping("/api/periodic/{jobId}")
    public ResponseEntity<PeriodicJobDto> status(@PathVariable("jobId") String jobId) {
        return scheduler.status(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
