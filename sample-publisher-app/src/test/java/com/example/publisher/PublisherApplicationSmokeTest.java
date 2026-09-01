package com.example.publisher;

import com.example.binaryserdes.MessageType;
import com.example.binaryserdes.ProtocolOut;
import com.example.restschema.RestApiDefinition;
import com.example.trafficconfig.TrafficToolConfig;
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
    private Map<String, ProtocolOut> interfaceProtocolOuts;

    @Autowired
    @Qualifier("restApiDefinitions")
    private Map<String, RestApiDefinition> restApiDefinitions;

    @Autowired
    private Map<String, List<MessageType>> interfaceMessageTypes;

    @Test
    void contextLoadsAgainstRealConfigWithNoMonitorScanning() {
        assertThat(trafficToolConfig.getInterfaces()).isNotEmpty();
        assertThat(interfaceProtocolOuts).containsKey("fruit");
        assertThat(interfaceMessageTypes).containsKey("fruit");
        assertThat(interfaceMessageTypes.get("fruit")).isNotEmpty();
        assertThat(restApiDefinitions).containsKey("pets");
    }
}
