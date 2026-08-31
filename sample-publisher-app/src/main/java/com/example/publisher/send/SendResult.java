package com.example.publisher.send;

import java.util.List;
import java.util.Map;

/**
 * Unified result of one send, whether UDP/TCP (fire-and-forget, {@code bytesSent} populated) or
 * REST (whole point is the response, {@code statusCode}/{@code responseBody} populated instead).
 */
public record SendResult(
        boolean success,
        Integer bytesSent,
        List<String> targets,
        Integer statusCode,
        Map<String, Object> responseBody,
        String error
) {

    public static SendResult sent(int bytesSent, List<String> targets) {
        return new SendResult(true, bytesSent, targets, null, null, null);
    }

    public static SendResult restResponse(int statusCode, List<String> targets, Map<String, Object> responseBody) {
        return new SendResult(true, null, targets, statusCode, responseBody, null);
    }

    public static SendResult failure(String error) {
        return new SendResult(false, null, List.of(), null, null, error);
    }
}
