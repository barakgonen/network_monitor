package com.example.monitor.rest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RestOperationRouterTest {

    private final RestOperationRouter router = new RestOperationRouter();

    private RestOperationDefinition operation(String operationId, String httpMethod, String pathTemplate) {
        return new RestOperationDefinition(
                "pets", operationId, httpMethod, pathTemplate, List.of(), List.of(), List.of(), null, Map.of(), null, false);
    }

    @Test
    void route_matchesSinglePathParameter_andExtractsItsValue() {
        RestApiDefinition api = new RestApiDefinition("pets", List.of(operation("getPet", "GET", "/pets/{petId}")));

        Optional<RestOperationRouter.RouteMatch> match = router.route(api, "GET", "/pets/42");

        assertThat(match).isPresent();
        assertThat(match.get().operation().operationId()).isEqualTo("getPet");
        assertThat(match.get().pathParams()).containsEntry("petId", "42");
    }

    @Test
    void route_matchesMultiplePathParameters_inOrder() {
        RestApiDefinition api = new RestApiDefinition(
                "pets", List.of(operation("getOwnerPet", "GET", "/owners/{ownerId}/pets/{petId}")));

        Optional<RestOperationRouter.RouteMatch> match = router.route(api, "GET", "/owners/7/pets/42");

        assertThat(match).isPresent();
        assertThat(match.get().pathParams()).containsEntry("ownerId", "7").containsEntry("petId", "42");
    }

    @Test
    void route_withNoPathParameters_matchesExactPathOnly() {
        RestApiDefinition api = new RestApiDefinition("pets", List.of(operation("createPet", "POST", "/pets")));

        assertThat(router.route(api, "POST", "/pets")).isPresent();
        assertThat(router.route(api, "POST", "/pets/extra")).isEmpty();
    }

    @Test
    void route_withMismatchedHttpMethod_doesNotMatch() {
        RestApiDefinition api = new RestApiDefinition("pets", List.of(operation("getPet", "GET", "/pets/{petId}")));

        assertThat(router.route(api, "POST", "/pets/42")).isEmpty();
    }

    @Test
    void route_withNoMatchingOperation_returnsEmpty() {
        RestApiDefinition api = new RestApiDefinition("pets", List.of(operation("getPet", "GET", "/pets/{petId}")));

        assertThat(router.route(api, "GET", "/nope")).isEmpty();
    }

    @Test
    void route_isCaseInsensitiveOnHttpMethod() {
        RestApiDefinition api = new RestApiDefinition("pets", List.of(operation("getPet", "GET", "/pets/{petId}")));

        assertThat(router.route(api, "get", "/pets/42")).isPresent();
    }
}
