package com.example.monitor.rest;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestSchemaConverterTest {

    private final RestSchemaConverter converter = new RestSchemaConverter();

    @Test
    void convert_objectWithScalarProperties_marksRequiredFieldsFromParentRequiredList() {
        Schema<Object> schema = new Schema<>();
        schema.setType("object");
        schema.setRequired(java.util.List.of("name"));

        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("name", new StringSchema());
        properties.put("nickname", new StringSchema());
        schema.setProperties(properties);

        RestSchemaNode node = converter.convert(schema, "pet");

        RestSchemaNode name = node.properties().stream().filter(p -> p.name().equals("name")).findFirst().orElseThrow();
        RestSchemaNode nickname = node.properties().stream().filter(p -> p.name().equals("nickname")).findFirst().orElseThrow();

        assertThat(name.required()).isTrue();
        assertThat(nickname.required()).isFalse();
    }

    @Test
    void convert_selfReferentialSchema_terminatesAtMaxDepth_insteadOfStackOverflow() {
        // Simulates a spec like `TreeNode { child: TreeNode }` after $ref resolution - a real
        // OpenAPI schema graph can legitimately be cyclic like this, unlike reflecting a fixed
        // Java class tree, which is why the depth guard matters more here.
        Schema<Object> selfReferencing = new Schema<>();
        selfReferencing.setType("object");
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("value", new StringSchema());
        properties.put("child", selfReferencing);
        selfReferencing.setProperties(properties);

        RestSchemaNode node = converter.convert(selfReferencing, "root");

        int depth = 0;
        RestSchemaNode current = node;
        while (current.properties() != null && depth < 20) {
            RestSchemaNode child = current.properties().stream()
                    .filter(p -> p.name().equals("child"))
                    .findFirst()
                    .orElseThrow();
            current = child;
            depth++;
        }

        // Recursion must have stopped well before the 20-iteration safety net above, proving the
        // depth guard fired rather than the loop just running out of patience.
        assertThat(depth).isLessThan(20);
        assertThat(current.properties()).isNull();
    }
}
