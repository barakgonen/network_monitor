package com.example.monitor.publisher;

import com.example.monitor.publisher.StubMessages.StubDedicatedPortMessage;
import com.example.monitor.publisher.StubMessages.StubLegacyMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublisherFieldMetadataServiceTest {

    private final PublisherFieldMetadataService service = new PublisherFieldMetadataService();

    @Test
    void describeFields_forRecord_usesComponentNamesAndTypes() {
        List<PublisherFieldDto> fields = service.describeFields(StubLegacyMessage.class);

        assertThat(fields).contains(
                new PublisherFieldDto("name", "String"),
                new PublisherFieldDto("calories", "double"));
    }

    @Test
    void describeFields_forGetterBasedClass_usesGetterNamesAndTypes() {
        List<PublisherFieldDto> fields = service.describeFields(StubDedicatedPortMessage.class);

        assertThat(fields).extracting(PublisherFieldDto::name).contains("radarSoftwareVersion", "statusFlags");
    }

    @Test
    void describeFields_forNestedComplexField_flattensToDottedPaths() {
        List<PublisherFieldDto> fields = service.describeFields(StubDedicatedPortMessage.class);

        assertThat(fields).contains(new PublisherFieldDto("header.msgType", "int"));
        assertThat(fields).extracting(PublisherFieldDto::name).doesNotContain("header");
    }
}
