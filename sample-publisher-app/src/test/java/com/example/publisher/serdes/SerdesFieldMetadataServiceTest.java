package com.example.publisher.serdes;

import com.example.binaryserdes.MessageType;
import com.example.binaryserdes.Protocol;
import com.example.binaryserdes.ProtocolIn;
import com.example.binaryserdes.config.ProtocolConfig;
import com.example.publisher.dto.FieldDto;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SerdesFieldMetadataServiceTest {

    private final SerdesFieldMetadataService service = new SerdesFieldMetadataService();

    private MessageType loadMessage(String repoRelativeSerdesFile, String messageName) throws Exception {
        Path path = Path.of("../" + repoRelativeSerdesFile);
        try (InputStream in = java.nio.file.Files.newInputStream(path)) {
            ProtocolConfig config = Protocol.loadConfig(in);
            ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(config);
            return protocolIn.getByName().get(messageName);
        }
    }

    @Test
    void describeFields_forFlatScalarMessage_returnsLeafFieldsOnly() throws Exception {
        MessageType orange = loadMessage("serdes/fruit.protocol.json", "Orange");

        List<FieldDto> fields = service.describeFields(orange);

        assertThat(fields).extracting(FieldDto::name).containsExactly("sourceFarm", "freshness");
        assertThat(fields).extracting(FieldDto::type).containsExactly("string", "string");
        assertThat(fields).allSatisfy(f -> assertThat(f.itemFields()).isNull());
    }

    @Test
    void describeFields_forMessageWithNestedRecordHeader_flattensToDottedPaths() throws Exception {
        MessageType radaStatus = loadMessage("serdes/rada.protocol.json", "RadaStatus");

        List<FieldDto> fields = service.describeFields(radaStatus);
        Map<String, FieldDto> byName = fields.stream().collect(Collectors.toMap(FieldDto::name, f -> f));

        assertThat(byName).containsKeys(
                "header.msgCounter", "header.msgType", "header.icdVersion", "header.msgSize",
                "radarSoftwareVersion", "recordingState", "workingMode", "statusFlags",
                "remainingRecordingSpace", "bitStatus", "manufacturerData");
        assertThat(byName.get("header.msgCounter").type()).isEqualTo("int32");
        assertThat(byName.get("radarSoftwareVersion").type()).isEqualTo("uint32");
    }

    @Test
    void describeFields_forArrayOfStructField_boxesItemFieldsWithMaxLength() throws Exception {
        MessageType tracksExtended = loadMessage("serdes/rada.protocol.json", "RadaTracksExtended");

        List<FieldDto> fields = service.describeFields(tracksExtended);
        Map<String, FieldDto> byName = fields.stream().collect(Collectors.toMap(FieldDto::name, f -> f));

        FieldDto trackData = byName.get("trackData");
        assertThat(trackData).isNotNull();
        assertThat(trackData.type()).isEqualTo("RadaTrackData[]");
        assertThat(trackData.maxLength()).isEqualTo(10);
        assertThat(trackData.itemFields()).isNotEmpty();
        assertThat(trackData.itemFields()).extracting(FieldDto::name).contains("id", "type", "latitudeRad");

        // A scalar array field (uint8[]) nested inside the struct element should stay a leaf,
        // not be boxed with itemFields, since its element type isn't a record.
        FieldDto reserved = trackData.itemFields().stream()
                .filter(f -> f.name().equals("reserved"))
                .findFirst().orElseThrow();
        assertThat(reserved.type()).isEqualTo("uint8[]");
        assertThat(reserved.itemFields()).isNull();
    }

    @Test
    void describeFields_forEnumField_populatesEnumValues() throws Exception {
        MessageType temperatureReading = loadMessage("serdes/weather.protocol.json", "TemperatureReading");

        List<FieldDto> fields = service.describeFields(temperatureReading);
        Map<String, FieldDto> byName = fields.stream().collect(Collectors.toMap(FieldDto::name, f -> f));

        FieldDto unit = byName.get("unit");
        assertThat(unit).isNotNull();
        assertThat(unit.type()).isEqualTo("TemperatureUnit");
        assertThat(unit.enumValues()).containsExactly("CELSIUS", "FAHRENHEIT");

        FieldDto temperature = byName.get("temperature");
        assertThat(temperature.enumValues()).isNull();
    }
}
