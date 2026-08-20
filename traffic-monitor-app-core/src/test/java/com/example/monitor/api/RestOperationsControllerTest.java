package com.example.monitor.api;

import com.example.monitor.rest.RestApiDefinition;
import com.example.monitor.rest.RestFieldMetadataService;
import com.example.monitor.rest.RestOperationDefinition;
import com.example.monitor.rest.RestSchemaNode;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc setup (no {@code @WebMvcTest} in Spring Boot 4, see
 * {@code AutoReplyControllerTest}) - real {@link TrafficToolConfig}/{@link RestApiDefinition}/
 * {@link RestFieldMetadataService} instances (all cheap, pure objects) rather than mocks, since
 * the point here is verifying the controller's JSON/query-param wiring, not re-testing logic
 * already covered by dedicated unit tests.
 */
class RestOperationsControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InterfaceConfig ordersConfig = new InterfaceConfig();
        ordersConfig.setKey("orders");
        ordersConfig.setName("Orders REST Interface");
        ordersConfig.setProtocol("REST");

        InterfaceConfig fruitConfig = new InterfaceConfig();
        fruitConfig.setKey("fruit");
        fruitConfig.setName("Fruit Interface");
        fruitConfig.setProtocol("UDP");

        TrafficToolConfig trafficToolConfig = new TrafficToolConfig();
        trafficToolConfig.setInterfaces(List.of(ordersConfig, fruitConfig));

        RestSchemaNode bodySchema = new RestSchemaNode("", "object", null,
                List.of(new RestSchemaNode("note", "string", null, null, null, null, false, null)),
                null, null, false, null);

        RestOperationDefinition getOrder = new RestOperationDefinition(
                "orders", "getOrder", "GET", "/orders/{orderId}", List.of(), List.of(), List.of(),
                null, Map.of(), "Fetch an order", false);
        RestOperationDefinition updateOrder = new RestOperationDefinition(
                "orders", "updateOrder", "PUT", "/orders/{orderId}", List.of(), List.of(), List.of(),
                bodySchema, Map.of(), "Update an order", true);

        RestApiDefinition ordersApi = new RestApiDefinition("orders", List.of(getOrder, updateOrder));

        RestOperationsController controller = new RestOperationsController(
                trafficToolConfig, Map.of("orders", ordersApi), new RestFieldMetadataService());

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void interfaces_listsOnlyRestInterfaces_withTheirOperations() throws Exception {
        mockMvc.perform(get("/api/rest/interfaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].key").value("orders"))
                .andExpect(jsonPath("$[0].operations.length()").value(2))
                .andExpect(jsonPath("$[0].operations[?(@.operationId == 'updateOrder')].httpMethod").value("PUT"));
    }

    @Test
    void fields_returnsFieldMetadata_forGivenOperation() throws Exception {
        mockMvc.perform(get("/api/rest/fields").param("interfaceKey", "orders").param("operationId", "updateOrder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("note"))
                .andExpect(jsonPath("$[0].type").value("string"));
    }

    @Test
    void fields_withUnknownInterfaceKey_returns400() throws Exception {
        mockMvc.perform(get("/api/rest/fields").param("interfaceKey", "unknown").param("operationId", "updateOrder"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fields_withUnknownOperationId_returns400() throws Exception {
        mockMvc.perform(get("/api/rest/fields").param("interfaceKey", "orders").param("operationId", "unknown"))
                .andExpect(status().isBadRequest());
    }
}
