package com.example.monitor.rest;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches an incoming HTTP method + request path against one interface's discovered operations,
 * extracting path-parameter values. There's no Spring MVC dispatcher available here - {@link
 * com.example.monitor.ingestion.rest.RestIngestionRunner} serves each REST interface off a raw
 * {@code com.sun.net.httpserver.HttpServer}/{@code HttpHandler}, not a Spring-managed servlet - so
 * path templates ({@code "/pets/{petId}"}) are compiled to a plain {@link Pattern} here instead of
 * relying on Spring's path-matching machinery. Compiled patterns are cached per {@link
 * RestOperationDefinition} (compile-once, reuse-forever) rather than named capture groups, since
 * Java's named-group syntax doesn't allow the hyphens/underscores some OpenAPI path parameter
 * names use - positional groups avoid that restriction entirely.
 */
@Component
public class RestOperationRouter {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    private final Map<RestOperationDefinition, CompiledRoute> compiledRoutes = new ConcurrentHashMap<>();

    public record RouteMatch(RestOperationDefinition operation, Map<String, String> pathParams) {
    }

    public Optional<RouteMatch> route(RestApiDefinition api, String httpMethod, String requestPath) {
        for (RestOperationDefinition operation : api.operations()) {
            if (!operation.httpMethod().equalsIgnoreCase(httpMethod)) {
                continue;
            }

            Optional<Map<String, String>> pathParams = compiledRoutes
                    .computeIfAbsent(operation, this::compile)
                    .match(requestPath);

            if (pathParams.isPresent()) {
                return Optional.of(new RouteMatch(operation, pathParams.get()));
            }
        }

        return Optional.empty();
    }

    private CompiledRoute compile(RestOperationDefinition operation) {
        String pathTemplate = operation.pathTemplate();
        List<String> paramNames = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");

        Matcher placeholderMatcher = PLACEHOLDER.matcher(pathTemplate);
        int lastEnd = 0;
        while (placeholderMatcher.find()) {
            regex.append(Pattern.quote(pathTemplate.substring(lastEnd, placeholderMatcher.start())));
            paramNames.add(placeholderMatcher.group(1));
            regex.append("([^/]+)");
            lastEnd = placeholderMatcher.end();
        }
        regex.append(Pattern.quote(pathTemplate.substring(lastEnd)));
        regex.append("$");

        return new CompiledRoute(Pattern.compile(regex.toString()), paramNames);
    }

    private record CompiledRoute(Pattern pattern, List<String> paramNames) {
        Optional<Map<String, String>> match(String requestPath) {
            Matcher matcher = pattern.matcher(requestPath);
            if (!matcher.matches()) {
                return Optional.empty();
            }

            Map<String, String> params = new LinkedHashMap<>();
            for (int i = 0; i < paramNames.size(); i++) {
                params.put(paramNames.get(i), matcher.group(i + 1));
            }
            return Optional.of(params);
        }
    }
}
