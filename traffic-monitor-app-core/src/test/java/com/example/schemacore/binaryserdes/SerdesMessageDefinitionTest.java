package com.example.schemacore.binaryserdes;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SerdesMessageDefinitionTest {

    private ProtocolConfig loadConfig() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/binaryserdes/protocol/candy.protocol.json")) {
            return Protocol.loadConfig(in);
        }
    }

    private SerdesMessageDefinition candyDefinition() throws Exception {
        ProtocolConfig config = loadConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(config);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(config);
        return new SerdesMessageDefinition("Candy Interface", "Candy", 4001, protocolIn, protocolOut);
    }

    @Test
    void messageClass_isNull_noBackingJavaClass() throws Exception {
        assertThat(candyDefinition().messageClass()).isNull();
    }

    @Test
    void encodeBody_thenDecodeBody_roundTripsFieldMap() throws Exception {
        MessageDefinition definition = candyDefinition();

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", "chocolate-bar");
        fields.put("calories", 250.5);

        byte[] encoded = definition.encodeBody(fields);
        Map<String, Object> decoded = definition.decodeBody(ByteBuffer.wrap(encoded));

        assertThat(decoded.get("name")).isEqualTo("chocolate-bar");
        assertThat(decoded.get("calories")).isEqualTo(250.5);
    }

    @Test
    void decodeMessage_returnsSameFieldMapAsDecodeBody() throws Exception {
        MessageDefinition definition = candyDefinition();
        byte[] encoded = definition.encodeBody(Map.of("name", "gum", "calories", 10.0));

        Object decodedMessage = definition.decodeMessage(ByteBuffer.wrap(encoded));

        assertThat(decodedMessage).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> decodedFields = (Map<String, Object>) decodedMessage;
        assertThat(decodedFields).containsEntry("name", "gum").containsEntry("calories", 10.0);
    }

    @Test
    void twoSerdesDefinitionsWithNullMessageClass_doNotCollideInRegistry() throws Exception {
        ProtocolConfig config = loadConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(config);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(config);

        SerdesMessageDefinition first = new SerdesMessageDefinition("Candy Interface", "Candy", 4001, protocolIn, protocolOut);
        SerdesMessageDefinition second = new SerdesMessageDefinition("Candy Interface", "OtherCandy", 4002, protocolIn, protocolOut);

        MessageDefinitionRegistry registry = new MessageDefinitionRegistry(List.of(first, second));

        assertThat(registry.findByOpcode(4001)).contains(first);
        assertThat(registry.findByOpcode(4002)).contains(second);
    }
}
