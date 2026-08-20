package com.example.monitor.rest;

import java.util.List;
import java.util.Map;

/**
 * The {@code operationId}-keyed analogue of {@link com.example.schemacore.MessageDefinition}/
 * opcode - REST operations have no backing {@code Class<?>} (fully dynamic, no codegen), so
 * {@code operationId} is the natural unique key within one interface's swagger file instead.
 */
public record RestOperationDefinition(
        String interfaceKey,
        String operationId,
        String httpMethod,
        String pathTemplate,
        List<RestParameterDefinition> pathParameters,
        List<RestParameterDefinition> queryParameters,
        List<RestParameterDefinition> headerParameters,
        RestSchemaNode requestBodySchema,
        Map<String, RestSchemaNode> responseSchemasByStatus,
        String summary,
        boolean requestBodyRequired
) {
}
