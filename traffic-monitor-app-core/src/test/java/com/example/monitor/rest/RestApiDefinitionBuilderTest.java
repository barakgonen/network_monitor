package com.example.monitor.rest;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RestApiDefinitionBuilderTest {

    private final RestSwaggerLoader loader = new RestSwaggerLoader();
    private final RestApiDefinitionBuilder builder = new RestApiDefinitionBuilder(new RestSchemaConverter());

    @Test
    void build_discoversOperationsPathAndQueryParams_andNestedRequestBodySchema() {
        OpenAPI openApi = loader.loadResolved(Path.of("../swagger/pets-demo.yml"));
        RestApiDefinition definition = builder.build("pets", openApi);

        assertThat(definition.operations()).hasSize(2);

        RestOperationDefinition getPet = definition.findByOperationId("getPet").orElseThrow();
        assertThat(getPet.httpMethod()).isEqualTo("GET");
        assertThat(getPet.pathTemplate()).isEqualTo("/pets/{petId}");
        assertThat(getPet.pathParameters()).extracting(RestParameterDefinition::name).containsExactly("petId");
        assertThat(getPet.queryParameters()).extracting(RestParameterDefinition::name).containsExactly("includeVaccinations");
        assertThat(getPet.requestBodySchema()).isNull();

        RestOperationDefinition createPet = definition.findByOperationId("createPet").orElseThrow();
        assertThat(createPet.httpMethod()).isEqualTo("POST");
        assertThat(createPet.requestBodyRequired()).isTrue();

        RestSchemaNode body = createPet.requestBodySchema();
        assertThat(body.type()).isEqualTo("object");
        assertThat(body.properties()).extracting(RestSchemaNode::name)
                .contains("name", "species", "age", "owner", "tags", "vaccinations");

        RestSchemaNode owner = body.properties().stream().filter(p -> p.name().equals("owner")).findFirst().orElseThrow();
        assertThat(owner.type()).isEqualTo("object");
        assertThat(owner.properties()).extracting(RestSchemaNode::name).containsExactlyInAnyOrder("name", "email");

        RestSchemaNode tags = body.properties().stream().filter(p -> p.name().equals("tags")).findFirst().orElseThrow();
        assertThat(tags.type()).isEqualTo("array");
        assertThat(tags.items().type()).isEqualTo("string");

        RestSchemaNode vaccinations = body.properties().stream().filter(p -> p.name().equals("vaccinations")).findFirst().orElseThrow();
        assertThat(vaccinations.type()).isEqualTo("array");
        assertThat(vaccinations.maxItems()).isEqualTo(10);
        assertThat(vaccinations.items().type()).isEqualTo("object");
        assertThat(vaccinations.items().properties()).extracting(RestSchemaNode::name)
                .containsExactlyInAnyOrder("vaccine", "administeredOn");
    }
}
