package com.example.monitor.rest;

/**
 * One OpenAPI operation parameter. {@code in} is {@code "path"}, {@code "query"}, or {@code
 * "header"}, mirroring {@code io.swagger.v3.oas.models.parameters.Parameter#getIn()}.
 */
public record RestParameterDefinition(String name, String in, RestSchemaNode schema, boolean required) {
}
