package com.example.schemacore.binaryserdes.protocol;

import com.example.schemacore.binaryserdes.Protocol;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.ProtocolOut;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import com.example.schemacore.binaryserdes.protocol.pojos.ComplexOfComplex;
import com.example.schemacore.binaryserdes.protocol.pojos.ComplexPosition;
import com.example.schemacore.binaryserdes.protocol.pojos.MultipleComplexMessage;
import com.example.schemacore.binaryserdes.protocol.pojos.MyPosition;
import com.example.schemacore.binaryserdes.protocol.pojos.MyPositionMessage;
import com.example.schemacore.binaryserdes.protocol.pojos.Position;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the vendored engine still round-trips composite ("record") types correctly - the
 * fruit/weather/ping/candy/greeting protocols generated for this project don't need records
 * (see the sibling *.protocol.json files under repo-root serdes/), but Rada (a later, currently
 * out-of-scope migration target) will, so this coverage matters for the vendored copy as a whole.
 */
class ConfigRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolConfig loadConfig(String resourcePath) throws Exception {
        InputStream in = getClass().getResourceAsStream(resourcePath);
        assertThat(in).withFailMessage("Config resource not found: " + resourcePath).isNotNull();
        return Protocol.loadConfig(in);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/binaryserdes/protocol/position-report-protocol.json"})
    void configShouldLoadAndCreateProtocols(String configResourcePath) throws Exception {
        ProtocolConfig cfg = loadConfig(configResourcePath);

        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        assertThat(protocolOut.getByName()).isNotEmpty();
        assertThat(protocolIn.getByName()).isNotEmpty();

        assertThat(protocolOut.getMessageType("PositionReport")).isNotNull();
        assertThat(protocolIn.getMessageType("PositionReport")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/binaryserdes/protocol/position-report-protocol.json"})
    void encodeShouldProduceExpectedMessageSize(String configResourcePath) throws Exception {
        ProtocolConfig cfg = loadConfig(configResourcePath);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        Position position = new Position(1, 111.111, 222.222, -333.333);
        String positionAsJson = MAPPER.writeValueAsString(position);

        byte[] bytes = protocolOut.encode(position.getOpcode(), positionAsJson);

        // opcode(int) = 4 bytes, lat/lon/alt(double) = 3 * 8 = 24 bytes -> total 28
        assertThat(bytes).hasSize(28);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/binaryserdes/protocol/position-report-protocol.json"})
    void jsonToBytesAndBack_shouldPreservePosition(String configResourcePath) throws Exception {
        ProtocolConfig cfg = loadConfig(configResourcePath);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        Position expectedPosition = new Position(1, 111.111, 222.222, -333.333);
        String positionAsJson = MAPPER.writeValueAsString(expectedPosition);

        byte[] bytes = protocolOut.encode(expectedPosition.getOpcode(), positionAsJson);

        String actualJson = protocolIn.parse("PositionReport", bytes);
        Position actualPosition = MAPPER.readValue(actualJson, Position.class);

        assertThat(actualPosition).usingRecursiveComparison().isEqualTo(expectedPosition);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/binaryserdes/protocol/position-report-protocol.json"})
    void jsonToBytesAndBack_shouldPreserveComplexFields(String configResourcePath) throws Exception {
        ProtocolConfig cfg = loadConfig(configResourcePath);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        MyPositionMessage expected = new MyPositionMessage(2, new MyPosition(65345, -1222, -733.333));
        String json = MAPPER.writeValueAsString(expected);

        byte[] bytes = protocolOut.encode(expected.getOpcode(), json);

        String actualJson = protocolIn.parse(expected.getOpcode(), bytes);
        MyPositionMessage actual = MAPPER.readValue(actualJson, MyPositionMessage.class);

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/binaryserdes/protocol/position-report-protocol.json"})
    void jsonToBytesAndBack_shouldPreserveMultipleComplexFields(String configResourcePath) throws Exception {
        ProtocolConfig cfg = loadConfig(configResourcePath);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        MyPosition first = new MyPosition(65345, -1222, -733.333);
        MyPosition four = new MyPosition(123, 5664, 234);
        MultipleComplexMessage message = new MultipleComplexMessage(3, first, 1, 2, 3.3, four);
        String json = MAPPER.writeValueAsString(message);

        byte[] bytes = protocolOut.encode(message.getOpcode(), json);

        String actualJson = protocolIn.parse(message.getOpcode(), bytes);
        MultipleComplexMessage actual = MAPPER.readValue(actualJson, MultipleComplexMessage.class);

        assertThat(actual).usingRecursiveComparison().isEqualTo(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/binaryserdes/protocol/position-report-protocol.json"})
    void jsonToBytesAndBack_shouldPreserveComplexOfComplex(String configResourcePath) throws Exception {
        ProtocolConfig cfg = loadConfig(configResourcePath);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        MyPosition first = new MyPosition(65345, -1222, -733.333);
        ComplexPosition complexPosition = new ComplexPosition(first, 1);
        ComplexOfComplex complexOfComplex = new ComplexOfComplex(4, complexPosition, 2);

        String json = MAPPER.writeValueAsString(complexOfComplex);

        byte[] bytes = protocolOut.encode(complexOfComplex.getOpcode(), json);

        String actualJson = protocolIn.parse(complexOfComplex.getOpcode(), bytes);
        ComplexOfComplex actual = MAPPER.readValue(actualJson, ComplexOfComplex.class);

        assertThat(actual).usingRecursiveComparison().isEqualTo(complexOfComplex);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/binaryserdes/protocol/position-report-protocol.json"})
    void jsonToBytesAndBack_shouldThrowExceptionWhenEncodingInvalidOpcode(String configResourcePath) throws Exception {
        ProtocolConfig cfg = loadConfig(configResourcePath);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        MyPosition first = new MyPosition(65345, -1222, -733.333);
        ComplexPosition complexPosition = new ComplexPosition(first, 1);
        ComplexOfComplex complexOfComplex = new ComplexOfComplex(5, complexPosition, 2);

        String json = MAPPER.writeValueAsString(complexOfComplex);

        assertThatThrownBy(() -> protocolOut.encode(complexOfComplex.getOpcode(), json))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
