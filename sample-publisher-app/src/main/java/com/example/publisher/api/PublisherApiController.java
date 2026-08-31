package com.example.publisher.api;

import com.example.publisher.dto.FieldDto;
import com.example.publisher.metadata.FieldMetadataService;
import com.example.publisher.metadata.PublishableInterfaceDto;
import com.example.publisher.metadata.PublishableInterfaceService;
import com.example.publisher.send.SendOrchestrationService;
import com.example.publisher.send.SendRequest;
import com.example.publisher.send.SendResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PublisherApiController {

    private final PublishableInterfaceService interfaceService;
    private final FieldMetadataService fieldMetadataService;
    private final SendOrchestrationService sendOrchestrationService;

    public PublisherApiController(
            PublishableInterfaceService interfaceService,
            FieldMetadataService fieldMetadataService,
            SendOrchestrationService sendOrchestrationService
    ) {
        this.interfaceService = interfaceService;
        this.fieldMetadataService = fieldMetadataService;
        this.sendOrchestrationService = sendOrchestrationService;
    }

    @GetMapping("/api/interfaces")
    public List<PublishableInterfaceDto> interfaces() {
        return interfaceService.list();
    }

    @GetMapping("/api/fields")
    public List<FieldDto> fields(
            @RequestParam("interfaceKey") String interfaceKey,
            @RequestParam("messageId") String messageId
    ) {
        return fieldMetadataService.describe(interfaceKey, messageId);
    }

    @PostMapping("/api/send")
    public SendResult send(@RequestBody SendRequest request) {
        return sendOrchestrationService.send(request);
    }
}
