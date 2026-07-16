package com.example.monitor;

import org.junit.jupiter.api.Test;

class TrafficMonitorApplicationContextLoadIT extends AbstractIntegrationTestBase {

    @Test
    void contextLoads() {
        // Verifies the full Spring context (UDP ingestion, handler wiring, REST controllers)
        // starts successfully with the real reflection-loaded MessageDefinitionRegistry.
    }
}
