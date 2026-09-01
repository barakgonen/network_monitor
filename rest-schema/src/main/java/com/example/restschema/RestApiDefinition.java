package com.example.restschema;

import java.util.List;
import java.util.Optional;

/**
 * All operations discovered from one REST interface's {@code swaggerFile}. Built once at startup
 * by {@link RestApiDefinitionBuilder} and exposed via the {@code restApiDefinitions} bean in each
 * consumer's own wiring config, keyed by interface key (see
 * {@code com.example.trafficconfig.InterfaceConfig#getKey()}).
 */
public record RestApiDefinition(String interfaceKey, List<RestOperationDefinition> operations) {

    public Optional<RestOperationDefinition> findByOperationId(String operationId) {
        return operations.stream()
                .filter(operation -> operation.operationId().equals(operationId))
                .findFirst();
    }
}
