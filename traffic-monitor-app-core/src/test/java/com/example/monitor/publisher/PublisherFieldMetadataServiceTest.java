package com.example.monitor.publisher;

import com.example.schemas.candy.CandyMessage;
import com.example.schemas.rada.messages.RadaStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublisherFieldMetadataServiceTest {

    private final PublisherFieldMetadataService service = new PublisherFieldMetadataService();

    @Test
    void describeFields_forRecord_usesComponentNamesAndTypes() {
        List<PublisherFieldDto> fields = service.describeFields(CandyMessage.class);

        assertThat(fields).contains(
                new PublisherFieldDto("name", "String"),
                new PublisherFieldDto("calories", "double"));
    }

    @Test
    void describeFields_forGetterBasedClass_usesGetterNamesAndTypes() {
        List<PublisherFieldDto> fields = service.describeFields(RadaStatus.class);

        assertThat(fields).extracting(PublisherFieldDto::name).contains("radarSoftwareVersion", "statusFlags", "header");
    }
}
