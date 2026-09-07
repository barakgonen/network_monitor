package com.example.publisher.serdes;

import com.example.binaryserdes.MessageType;
import com.example.binaryserdes.Protocol;
import com.example.binaryserdes.ProtocolIn;
import com.example.binaryserdes.config.ProtocolConfig;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SerdesRequestBodyAssemblerTest {

    private final SerdesRequestBodyAssembler assembler = new SerdesRequestBodyAssembler();

    private MessageType loadMessage(String repoRelativeSerdesFile, String messageName) throws Exception {
        Path path = Path.of("../" + repoRelativeSerdesFile);
        try (InputStream in = java.nio.file.Files.newInputStream(path)) {
            ProtocolConfig config = Protocol.loadConfig(in);
            ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(config);
            return protocolIn.getByName().get(messageName);
        }
    }

    @Test
    void assemble_coercesFlatScalarStringFieldsFromHtmlFormStrings() throws Exception {
        MessageType orange = loadMessage("serdes/fruit.protocol.json", "Orange");

        Map<String, Object> result = assembler.assemble(orange, Map.of(
                "sourceFarm", "north-farm-17",
                "freshness", "very_fresh"));

        assertThat(result).containsEntry("sourceFarm", "north-farm-17");
        assertThat(result).containsEntry("freshness", "very_fresh");
    }

    @Test
    void assemble_coercesNumericStringsToCorrectBoxedTypes() throws Exception {
        MessageType banana = loadMessage("serdes/fruit.protocol.json", "Banana");

        Map<String, Object> result = assembler.assemble(banana, Map.of(
                "color", "yellow",
                "weight", "123.5"));

        assertThat(result.get("weight")).isInstanceOf(Double.class).isEqualTo(123.5);
    }

    @Test
    void assemble_regroupsDottedNestedRecordFields() throws Exception {
        MessageType radaStatus = loadMessage("serdes/rada.protocol.json", "RadaStatus");

        Map<String, Object> result = assembler.assemble(radaStatus, Map.of(
                "header.msgCounter", "1",
                "header.msgType", "3",
                "radarSoftwareVersion", "42"));

        assertThat(result.get("header")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) result.get("header");
        assertThat(header).containsEntry("msgCounter", 1).containsEntry("msgType", 3);
        // Fields the caller didn't submit still get a default value, not left missing -
        // MessageType.writeToBuffer throws on a genuinely missing/null field.
        assertThat(header).containsKeys("icdVersion", "reserved1", "reserved2", "reserved3", "msgSize");
        assertThat(result.get("radarSoftwareVersion")).isEqualTo(42);
    }

    @Test
    void assemble_padsFixedLengthArrayOfStructToFullLength() throws Exception {
        MessageType tracksExtended = loadMessage("serdes/rada.protocol.json", "RadaTracksExtended");

        Map<String, Object> result = assembler.assemble(tracksExtended, Map.of(
                "trackData[0].id", "7",
                "trackData[2].id", "9"));

        assertThat(result.get("trackData")).isInstanceOf(List.class);
        List<?> trackData = (List<?>) result.get("trackData");
        assertThat(trackData).hasSize(10);

        @SuppressWarnings("unchecked")
        Map<String, Object> item0 = (Map<String, Object>) trackData.get(0);
        assertThat(item0.get("id")).isEqualTo(7);

        @SuppressWarnings("unchecked")
        Map<String, Object> item1 = (Map<String, Object>) trackData.get(1);
        assertThat(item1.get("id")).isEqualTo(0); // padded default, not missing

        @SuppressWarnings("unchecked")
        Map<String, Object> item2 = (Map<String, Object>) trackData.get(2);
        assertThat(item2.get("id")).isEqualTo(9);
    }
}
