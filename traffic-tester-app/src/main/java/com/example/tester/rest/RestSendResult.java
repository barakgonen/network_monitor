package com.example.tester.rest;

/** The outcome of one {@link RestPublisher} call - {@code method} is whatever was actually sent (after resolving any {@code PetsPayloadConfig#getMethod()} override), for logging. */
public record RestSendResult(String method, int statusCode, String body) {
}
