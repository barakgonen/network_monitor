package com.example.monitor.publisher;

import java.util.List;

public record PublisherSendResponse(
        boolean success,
        int bytesSent,
        List<String> targets,
        String error
) {
}
