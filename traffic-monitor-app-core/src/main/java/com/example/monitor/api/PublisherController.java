package com.example.monitor.api;

import com.example.monitor.publisher.PublisherFieldDto;
import com.example.monitor.publisher.PublisherFieldMetadataService;
import com.example.monitor.publisher.PublisherInterfaceDto;
import com.example.monitor.publisher.PublisherMetadataService;
import com.example.monitor.publisher.PublisherSendRequest;
import com.example.monitor.publisher.PublisherSendResponse;
import com.example.monitor.publisher.PublisherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PublisherController {
    private final PublisherMetadataService metadataService;
    private final PublisherFieldMetadataService fieldMetadataService;
    private final PublisherService publisherService;

    public PublisherController(
            PublisherMetadataService metadataService,
            PublisherFieldMetadataService fieldMetadataService,
            PublisherService publisherService
    ) {
        this.metadataService = metadataService;
        this.fieldMetadataService = fieldMetadataService;
        this.publisherService = publisherService;
    }

    @GetMapping("/api/publisher/interfaces")
    public List<PublisherInterfaceDto> interfaces() {
        return metadataService.interfaces();
    }

    @GetMapping("/api/publisher/fields")
    public List<PublisherFieldDto> fields(@RequestParam("messageClass") String messageClass) throws ClassNotFoundException {
        return fieldMetadataService.describeFields(Class.forName(messageClass));
    }

    @PostMapping("/api/publisher/send")
    public PublisherSendResponse send(@RequestBody PublisherSendRequest request) {
        return publisherService.send(request);
    }
}
