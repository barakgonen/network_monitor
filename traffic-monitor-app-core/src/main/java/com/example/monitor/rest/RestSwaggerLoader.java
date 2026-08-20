package com.example.monitor.rest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Parses one OpenAPI/Swagger YAML file into a fully-resolved {@link OpenAPI} model - {@code $ref}
 * and {@code allOf}/{@code oneOf}/{@code anyOf} are all resolved up front so {@link
 * RestApiDefinitionBuilder}/{@link RestSchemaConverter} never have to chase a reference
 * themselves. Fails fast at startup (mirrors {@link com.example.monitor.schema.TrafficToolConfigLoader}'s
 * philosophy) rather than on the first request against a malformed spec.
 */
@Component
public class RestSwaggerLoader {

    public OpenAPI loadResolved(Path swaggerFile) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        parseOptions.setResolveFully(true);
        parseOptions.setResolveCombinators(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(swaggerFile.toString(), null, parseOptions);

        if (result.getOpenAPI() == null) {
            throw new IllegalArgumentException(
                    "Failed to parse OpenAPI file " + swaggerFile + ": " + result.getMessages());
        }

        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            throw new IllegalArgumentException(
                    "OpenAPI file " + swaggerFile + " parsed with errors: " + result.getMessages());
        }

        return result.getOpenAPI();
    }
}
