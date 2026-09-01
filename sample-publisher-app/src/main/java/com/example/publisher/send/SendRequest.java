package com.example.publisher.send;

import java.util.Map;

/**
 * {@code messageId} is a serdes message name for UDP/TCP interfaces, or a REST {@code operationId}
 * for REST interfaces. {@code transport} is optional - defaults to the interface's own configured
 * protocol (UDP/TCP) when blank; only meaningful to override for a UDP/TCP interface (REST always
 * sends REST). {@code fields} are flattened dotted/indexed form values, same convention as the
 * rest of this app's field-description/assembly.
 */
public record SendRequest(
        String interfaceKey,
        String messageId,
        String host,
        Integer port,
        String transport,
        Map<String, Object> fields
) {
}
