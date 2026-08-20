package com.example.monitor.rest;

import java.util.List;
import java.util.Optional;

/**
 * All operations discovered from one REST interface's {@code swaggerFile}. Built once at startup
 * by {@link RestApiDefinitionBuilder} and exposed via the {@code restApiDefinitions} bean in
 * {@link RestSchemaWiringConfig}, keyed by {@link com.example.monitor.schema.InterfaceConfig#getKey()}.
 */
public record RestApiDefinition(String interfaceKey, List<RestOperationDefinition> operations) {

    public Optional<RestOperationDefinition> findByOperationId(String operationId) {
        return operations.stream()
                .filter(operation -> operation.operationId().equals(operationId))
                .findFirst();
    }
}
