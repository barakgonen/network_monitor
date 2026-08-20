package com.example.monitor.rest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a parsed {@link OpenAPI} model's paths/operations into a {@link RestApiDefinition} -
 * operations are auto-discovered here rather than hand-declared in {@code traffic-tool.yml},
 * since there's no per-operation Java class to wire up. JSON request/response bodies only
 * (v1 scope) - other media types are skipped with a warning rather than supported.
 */
@Component
public class RestApiDefinitionBuilder {
    private static final Logger log = LoggerFactory.getLogger(RestApiDefinitionBuilder.class);
    private static final String JSON_MEDIA_TYPE = "application/json";

    private final RestSchemaConverter schemaConverter;

    public RestApiDefinitionBuilder(RestSchemaConverter schemaConverter) {
        this.schemaConverter = schemaConverter;
    }

    public RestApiDefinition build(String interfaceKey, OpenAPI openApi) {
        List<RestOperationDefinition> operations = new ArrayList<>();

        if (openApi.getPaths() != null) {
            for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
                String pathTemplate = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();

                for (Map.Entry<PathItem.HttpMethod, Operation> operationEntry : pathItem.readOperationsMap().entrySet()) {
                    operations.add(toOperationDefinition(
                            interfaceKey, pathTemplate, operationEntry.getKey().name(), operationEntry.getValue(), pathItem));
                }
            }
        }

        return new RestApiDefinition(interfaceKey, operations);
    }

    private RestOperationDefinition toOperationDefinition(
            String interfaceKey, String pathTemplate, String httpMethod, Operation operation, PathItem pathItem) {
        String operationId = operation.getOperationId();
        if (operationId == null || operationId.isBlank()) {
            operationId = httpMethod + "_" + pathTemplate.replace('/', '_');
            log.warn("Operation {} {} on interface {} has no operationId - synthesized '{}'",
                    httpMethod, pathTemplate, interfaceKey, operationId);
        }

        List<RestParameterDefinition> pathParameters = new ArrayList<>();
        List<RestParameterDefinition> queryParameters = new ArrayList<>();
        List<RestParameterDefinition> headerParameters = new ArrayList<>();

        collectParameters(pathItem.getParameters(), pathParameters, queryParameters, headerParameters);
        collectParameters(operation.getParameters(), pathParameters, queryParameters, headerParameters);

        RestSchemaNode requestBodySchema = null;
        boolean requestBodyRequired = false;
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null) {
            Schema<?> schema = jsonSchema(requestBody.getContent(), interfaceKey, httpMethod, pathTemplate);
            if (schema != null) {
                requestBodySchema = schemaConverter.convert(schema, "");
                requestBodyRequired = Boolean.TRUE.equals(requestBody.getRequired());
            }
        }

        Map<String, RestSchemaNode> responseSchemasByStatus = new LinkedHashMap<>();
        if (operation.getResponses() != null) {
            for (Map.Entry<String, ApiResponse> responseEntry : operation.getResponses().entrySet()) {
                Schema<?> schema = jsonSchema(responseEntry.getValue().getContent(), interfaceKey, httpMethod, pathTemplate);
                if (schema != null) {
                    responseSchemasByStatus.put(responseEntry.getKey(), schemaConverter.convert(schema, ""));
                }
            }
        }

        return new RestOperationDefinition(
                interfaceKey, operationId, httpMethod, pathTemplate,
                pathParameters, queryParameters, headerParameters,
                requestBodySchema, responseSchemasByStatus, operation.getSummary(), requestBodyRequired);
    }

    private void collectParameters(
            List<Parameter> parameters,
            List<RestParameterDefinition> pathParameters,
            List<RestParameterDefinition> queryParameters,
            List<RestParameterDefinition> headerParameters) {
        if (parameters == null) {
            return;
        }

        for (Parameter parameter : parameters) {
            RestParameterDefinition definition = new RestParameterDefinition(
                    parameter.getName(),
                    parameter.getIn(),
                    schemaConverter.convert(parameter.getSchema(), parameter.getName()),
                    Boolean.TRUE.equals(parameter.getRequired()));

            switch (parameter.getIn()) {
                case "path" -> pathParameters.add(definition);
                case "query" -> queryParameters.add(definition);
                case "header" -> headerParameters.add(definition);
                default -> log.debug("Skipping parameter {} with unsupported location '{}'",
                        parameter.getName(), parameter.getIn());
            }
        }
    }

    private Schema<?> jsonSchema(io.swagger.v3.oas.models.media.Content content, String interfaceKey, String httpMethod, String pathTemplate) {
        if (content == null) {
            return null;
        }

        MediaType mediaType = content.get(JSON_MEDIA_TYPE);
        if (mediaType == null) {
            if (!content.isEmpty()) {
                log.warn("Operation {} {} on interface {} has no application/json media type (found {}) - skipping",
                        httpMethod, pathTemplate, interfaceKey, content.keySet());
            }
            return null;
        }

        return mediaType.getSchema();
    }
}
