package com.example.monitor.rest;

/** A user-configured static REST server-mode response for one {@code (interfaceKey, operationId)}. */
public record RestAutoReplyConfig(int statusCode, String bodyTemplate) {
}
