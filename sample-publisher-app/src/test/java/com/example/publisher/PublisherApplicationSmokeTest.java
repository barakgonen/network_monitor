package com.example.publisher;

import com.example.monitor.rest.RestApiDefinition;
import com.example.monitor.schema.TrafficToolConfig;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.binaryserdes.MessageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "traffic.tool.config-path=src/test/resources/traffic-tool-test.yml"
})
class PublisherApplicationSmokeTest {

    @Autowired
    private TrafficToolConfig trafficToolConfig;

    @Autowired
    @Qualifier("interfaceMessageDefinitionRegistries")
    private Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries;

    @Autowired
    @Qualifier("restApiDefinitions")
    private Map<String, RestApiDefinition> restApiDefinitions;

    @Autowired
    private Map<String, List<MessageType>> interfaceMessageTypes;

    @Test
    void contextLoadsAgainstRealConfigWithNoMonitorScanning() {
        assertThat(trafficToolConfig.getInterfaces()).isNotEmpty();
        assertThat(interfaceMessageDefinitionRegistries).containsKey("fruit");
        assertThat(interfaceMessageDefinitionRegistries.get("fruit").all()).isNotEmpty();
        assertThat(interfaceMessageTypes).containsKey("fruit");
        assertThat(interfaceMessageTypes.get("fruit")).isNotEmpty();
        assertThat(restApiDefinitions).containsKey("pets");
    }
}
