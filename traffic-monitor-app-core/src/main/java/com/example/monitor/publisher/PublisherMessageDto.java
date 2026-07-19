package com.example.monitor.publisher;

public record PublisherMessageDto(String type, String messageClass, int opcode) {
}
