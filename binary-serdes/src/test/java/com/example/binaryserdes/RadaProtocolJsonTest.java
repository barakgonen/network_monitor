package com.example.binaryserdes;

import com.example.binaryserdes.config.ProtocolConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity-checks the real repo-root serdes/rada.protocol.json (not a test fixture copy) - loads it
 * the same way MessageSchemaWiringConfig does, and round-trips each of the 4 rada messages,
 * including RadaTracksExtended's nested array-of-record fields.
 */
class RadaProtocolJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolConfig loadRealRadaProtocolConfig() throws Exception {
        Path path = findRepoRootSerdesFile();
        try (InputStream in = Files.newInputStream(path)) {
            return Protocol.loadConfig(in);
        }
    }

    /** Test runs with the module directory as CWD (Maven default), so walk up to the repo root. */
    private static Path findRepoRootSerdesFile() {
        Path candidate = Paths.get("serdes/rada.protocol.json");
        if (Files.exists(candidate)) {
            return candidate;
        }
        return Paths.get("../serdes/rada.protocol.json");
    }

    @Test
    void radaExtendedStatus_roundTrips() throws Exception {
        ProtocolConfig cfg = loadRealRadaProtocolConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        String json = """
                {
                  "header": {"msgCounter": 1, "msgType": 1, "icdVersion": 2, "reserved1": 0, "reserved2": 0, "reserved3": 0, "msgSize": 0},
                  "latitude": 32.0853, "longitude": 34.7818, "altitude": 12.5,
                  "pitch": 1.0, "roll": 2.0, "heading": 3.0,
                  "coverage1Sector1": 4.0, "coverage1Sector2": 5.0, "coverage1Radius": 6.0
                }
                """;

        byte[] bytes = protocolOut.encode("RadaExtendedStatus", json);
        // RadaHeader(16) + lat/lon(16) + 7 floats(28) = 60
        assertThat(bytes).hasSize(60);

        JsonNode decoded = MAPPER.readTree(protocolIn.parse(1, bytes));
        assertThat(decoded.get("header").get("msgCounter").asInt()).isEqualTo(1);
        assertThat(decoded.get("header").get("msgType").asInt()).isEqualTo(1);
        assertThat(decoded.get("latitude").asDouble()).isEqualTo(32.0853);
        assertThat(decoded.get("altitude").asDouble()).isEqualTo(12.5);
    }

    @Test
    void radaStatus_roundTrips() throws Exception {
        ProtocolConfig cfg = loadRealRadaProtocolConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        String json = """
                {
                  "header": {"msgCounter": 5, "msgType": 3, "icdVersion": 1, "reserved1": 0, "reserved2": 0, "reserved3": 0, "msgSize": 0},
                  "radarSoftwareVersion": 7, "recordingState": 1, "workingMode": 2,
                  "statusFlags": 100, "remainingRecordingSpace": 200, "bitStatus": 3, "manufacturerData": 50
                }
                """;

        byte[] bytes = protocolOut.encode("RadaStatus", json);
        // RadaHeader(16) + 3 uint32(12) + 2 uint16(4) + uint32(4) + uint16(2) = 38
        assertThat(bytes).hasSize(38);

        JsonNode decoded = MAPPER.readTree(protocolIn.parse(3, bytes));
        assertThat(decoded.get("radarSoftwareVersion").asLong()).isEqualTo(7);
        assertThat(decoded.get("manufacturerData").asInt()).isEqualTo(50);
    }

    @Test
    void radaTracksExtended_roundTripsNestedArraysOfRecords() throws Exception {
        ProtocolConfig cfg = loadRealRadaProtocolConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        String trackDataJson = """
                {"id": 1, "type": 0, "reserved": %s, "latitudeRad": 0.1, "longtitudeRad": 0.2,
                 "altitudeM": 1.0, "dopplerVelocity": 2.0, "radarCrossSection": 3.0, "reserved2": %s,
                 "statusFlags": 1, "reserved3": %s, "associatedPlots": 0,
                 "timeSinceLastAssociation": 0.0, "age": 0.0, "posX": 0.0, "posY": 0.0, "posZ": 0.0,
                 "velocityX": 0.0, "velocityY": 0.0, "velocityZ": 0.0,
                 "posErrorX": 0.0, "posErrorY": 0.0, "posErrorZ": 0.0,
                 "velocityErrorX": 0.0, "velocityErrorY": 0.0, "velocityErrorZ": 0.0,
                 "testParam0": 0.0, "testParam1": 0.0, "testParam2": 0.0, "testParam3": 0.0,
                 "testParam4": 0.0, "testParam5": 0.0, "testParam6": 0.0, "testParam7": 0.0,
                 "testParam8": 0.0, "testParam9": 0.0}
                """.formatted(zeroArray(40), zeroArray(44), zeroArray(10));

        String plotDataJson = """
                {"range": 1.0, "rangSTD": 0.1, "dopplerVelocity": 2.0, "dopplerVelocitySTD": 0.2,
                 "azimuth": 3.0, "azimuthSTD": 0.3, "elevation": 4.0, "elevationSTD": 0.4,
                 "snr": 5.0, "rcs": 6.0, "reserved": 0}
                """;

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"header\": {\"msgCounter\": 1, \"msgType\": 4, \"icdVersion\": 0, \"reserved1\": 0, \"reserved2\": 0, \"reserved3\": 0, \"msgSize\": 0},");
        json.append("\"updateTimeTag\": 123456789, \"chunkNumber\": 1, \"totalNumberIOfReportedTracks\": 2,");
        json.append("\"reserved\": 0, \"numberOfTracksInThisMessage\": 2, \"tracksTagYear\": 2026,");
        json.append("\"tracksTagMonth\": 1, \"tracksTagDayOfMonth\": 1, \"tracksTagHour\": 0,");
        json.append("\"tracksTagMinute\": 0, \"tracksTagSecond\": 0, \"tracksTagMillisecond\": 0, \"reserved1\": 0,");
        json.append("\"trackData\": [").append(repeat(trackDataJson, 10)).append("],");
        json.append("\"plotData\": [").append(repeat(plotDataJson, 10)).append("]");
        json.append("}");

        byte[] bytes = protocolOut.encode("RadaTracksExtended", json.toString());
        // header(16) + updateTimeTag(8) + 12 uint16(24) + trackData(232*10=2320) + plotData(44*10=440)
        assertThat(bytes).hasSize(16 + 8 + 24 + 2320 + 440);

        JsonNode decoded = MAPPER.readTree(protocolIn.parse(4, bytes));
        assertThat(decoded.get("trackData")).hasSize(10);
        assertThat(decoded.get("plotData")).hasSize(10);
        assertThat(decoded.get("trackData").get(0).get("latitudeRad").asDouble()).isEqualTo(0.1);
        assertThat(decoded.get("plotData").get(0).get("range").asDouble()).isEqualTo(1.0);
    }

    private static String zeroArray(int length) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(",");
            sb.append("0");
        }
        return sb.append("]").toString();
    }

    private static String repeat(String json, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            if (i > 0) sb.append(",");
            sb.append(json);
        }
        return sb.toString();
    }
}
