package com.example.monitor.api.publisher;

import com.example.monitor.publisher.PublisherMetadataService;
import com.example.monitor.publisher.PublisherService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/publisher")
public class PublisherController {
    private final PublisherMetadataService metadataService;
    private final PublisherService publisherService;

    public PublisherController(PublisherMetadataService metadataService, PublisherService publisherService) {
        this.metadataService = metadataService;
        this.publisherService = publisherService;
    }

    @GetMapping("/interfaces")
    public List<PublisherInterfaceDto> interfaces() {
        return metadataService.interfaces();
    }

    @PostMapping("/send")
    public PublisherSendResponse send(@RequestBody PublisherSendRequest request) {
        return publisherService.send(request);
    }
}
