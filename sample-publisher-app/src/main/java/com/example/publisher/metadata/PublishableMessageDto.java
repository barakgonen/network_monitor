package com.example.publisher.metadata;

/**
 * One sendable message (serdes UDP/TCP) or operation (REST) within a {@link PublishableInterfaceDto}.
 * {@code id} is what callers pass back as {@code messageId} to {@code /api/fields}/{@code /api/send}
 * - a serdes message name, or a REST {@code operationId}. {@code label} is display-only.
 */
public record PublishableMessageDto(String id, String label) {
}
