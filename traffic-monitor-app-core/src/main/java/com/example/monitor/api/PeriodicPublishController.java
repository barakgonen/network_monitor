package com.example.monitor.api;

import com.example.monitor.publishing.PeriodicPublisherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PeriodicPublishController {
    private final PeriodicPublisherService periodicPublisherService;

    public PeriodicPublishController(PeriodicPublisherService periodicPublisherService) {
        this.periodicPublisherService = periodicPublisherService;
    }

    @PostMapping("/api/publish/udp/periodic/start")
    public PeriodicPublishStatus start(@RequestBody PeriodicPublishRequest request) {
        return periodicPublisherService.start(request);
    }

    @PostMapping("/api/publish/udp/periodic/stop")
    public PeriodicPublishStatus stop() {
        return periodicPublisherService.stop();
    }

    @GetMapping("/api/publish/udp/periodic/status")
    public PeriodicPublishStatus status() {
        return periodicPublisherService.status();
    }
}
