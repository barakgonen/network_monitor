package com.example.monitor.rest;

import com.example.monitor.publisher.PublisherFieldDto;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestFieldMetadataServiceAndAssemblerTest {

    private final RestSwaggerLoader loader = new RestSwaggerLoader();
    private final RestApiDefinitionBuilder apiDefinitionBuilder = new RestApiDefinitionBuilder(new RestSchemaConverter());
    private final RestFieldMetadataService fieldMetadataService = new RestFieldMetadataService();
    private final RestRequestBodyAssembler assembler = new RestRequestBodyAssembler();

    private RestOperationDefinition createPetOperation() {
        OpenAPI openApi = loader.loadResolved(Path.of("../swagger/pets-demo.yml"));
        RestApiDefinition definition = apiDefinitionBuilder.build("pets", openApi);
        return definition.findByOperationId("createPet").orElseThrow();
    }

    @Test
    void describeFields_flattensNestedObject_andBoxesArrayOfStructSeparately() {
        List<PublisherFieldDto> fields = fieldMetadataService.describeFields(createPetOperation().requestBodySchema());

        assertThat(fields).extracting(PublisherFieldDto::name)
                .contains("name", "species", "age", "owner.name", "owner.email", "tags", "vaccinations");

        PublisherFieldDto vaccinations = fields.stream().filter(f -> f.name().equals("vaccinations")).findFirst().orElseThrow();
        assertThat(vaccinations.maxLength()).isEqualTo(10);
        assertThat(vaccinations.itemFields()).extracting(PublisherFieldDto::name).containsExactlyInAnyOrder("vaccine", "administeredOn");

        PublisherFieldDto tags = fields.stream().filter(f -> f.name().equals("tags")).findFirst().orElseThrow();
        assertThat(tags.itemFields()).isNull();
        assertThat(tags.type()).isEqualTo("string[]");
    }

    @Test
    void assemble_regroupsDottedAndIndexedFields_intoTypedJsonBody() {
        Map<String, Object> flatFields = Map.of(
                "name", "Rex",
                "age", "3",
                "owner.name", "Alice",
                "owner.email", "alice@example.com",
                "vaccinations[0].vaccine", "Rabies",
                "vaccinations[1].vaccine", "Distemper"
        );

        Map<String, Object> body = assembler.assemble(createPetOperation().requestBodySchema(), flatFields);

        assertThat(body.get("name")).isEqualTo("Rex");
        assertThat(body.get("age")).isEqualTo(3);

        @SuppressWarnings("unchecked")
        Map<String, Object> owner = (Map<String, Object>) body.get("owner");
        assertThat(owner.get("name")).isEqualTo("Alice");
        assertThat(owner.get("email")).isEqualTo("alice@example.com");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vaccinations = (List<Map<String, Object>>) body.get("vaccinations");
        assertThat(vaccinations).hasSize(2);
        assertThat(vaccinations.get(0).get("vaccine")).isEqualTo("Rabies");
        assertThat(vaccinations.get(1).get("vaccine")).isEqualTo("Distemper");
    }
}
