package com.example.monitor.persistence;

import com.example.monitor.model.ObservedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class MessageArchiveRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MessageArchiveRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MessageArchiveRepository(jdbcTemplate, new ObjectMapper());
    }

    private static ObservedMessage message(String id, Instant observedAt, String interfaceName, String messageType,
                                            String parseError) {
        return new ObservedMessage(
                id,
                observedAt,
                "UDP",
                "127.0.0.1:1234",
                5001,
                interfaceName,
                messageType,
                Map.of("opcode", 1001),
                Map.of("sourceFarm", "farm-x", "freshness", "very_fresh"),
                27,
                "payload-text",
                "cGF5bG9hZA==",
                parseError
        );
    }

    @Test
    void save_thenFindHistory_roundTripsAllFieldsIncludingJson() {
        ObservedMessage message = message(UUID.randomUUID().toString(), Instant.now(), "Fruit Interface", "Orange", null);

        repository.save(message);

        HistoryPage page = repository.findHistory(new HistoryQuery(null, null, false, null, null, 50, 0));

        assertThat(page.totalCount()).isEqualTo(1);
        ObservedMessage found = page.items().get(0);
        assertThat(found.id()).isEqualTo(message.id());
        assertThat(found.transportProtocol()).isEqualTo("UDP");
        assertThat(found.remoteAddress()).isEqualTo("127.0.0.1:1234");
        assertThat(found.localPort()).isEqualTo(5001);
        assertThat(found.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(found.messageType()).isEqualTo("Orange");
        assertThat(found.header()).containsEntry("opcode", 1001);
        assertThat(found.body()).containsEntry("sourceFarm", "farm-x").containsEntry("freshness", "very_fresh");
        assertThat(found.payloadSizeBytes()).isEqualTo(27);
        assertThat(found.payloadText()).isEqualTo("payload-text");
        assertThat(found.payloadBase64()).isEqualTo("cGF5bG9hZA==");
        assertThat(found.parseError()).isNull();
        assertThat(found.observedAt()).isCloseTo(message.observedAt(), org.assertj.core.api.Assertions.within(1, ChronoUnit.SECONDS));
    }

    @Test
    void findHistory_withEmptyTable_returnsEmptyPageNotError() {
        HistoryPage page = repository.findHistory(new HistoryQuery(null, null, false, null, null, 50, 0));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalCount()).isZero();
    }

    @Test
    void findHistory_filtersByInterfaceName() {
        repository.save(message(UUID.randomUUID().toString(), Instant.now(), "Fruit Interface", "Orange", null));
        repository.save(message(UUID.randomUUID().toString(), Instant.now(), "Weather Interface", "TemperatureReading", null));

        HistoryPage page = repository.findHistory(new HistoryQuery("Fruit Interface", null, false, null, null, 50, 0));

        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items().get(0).interfaceName()).isEqualTo("Fruit Interface");
    }

    @Test
    void findHistory_filtersByMessageType() {
        repository.save(message(UUID.randomUUID().toString(), Instant.now(), "Fruit Interface", "Orange", null));
        repository.save(message(UUID.randomUUID().toString(), Instant.now(), "Fruit Interface", "Banana", null));

        HistoryPage page = repository.findHistory(new HistoryQuery(null, "Banana", false, null, null, 50, 0));

        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items().get(0).messageType()).isEqualTo("Banana");
    }

    @Test
    void findHistory_withParseErrorOnly_returnsOnlyErroredMessages() {
        repository.save(message(UUID.randomUUID().toString(), Instant.now(), "Fruit Interface", "Orange", null));
        repository.save(message(UUID.randomUUID().toString(), Instant.now(), "Unknown", "Unknown", "Unknown opcode: 9"));

        HistoryPage page = repository.findHistory(new HistoryQuery(null, null, true, null, null, 50, 0));

        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items().get(0).parseError()).isEqualTo("Unknown opcode: 9");
    }

    @Test
    void findHistory_filtersByTimeRange() {
        Instant old = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant recent = Instant.now();

        repository.save(message(UUID.randomUUID().toString(), old, "Fruit Interface", "Orange", null));
        repository.save(message(UUID.randomUUID().toString(), recent, "Fruit Interface", "Orange", null));

        HistoryPage page = repository.findHistory(new HistoryQuery(
                null, null, false, recent.minus(1, ChronoUnit.HOURS), recent.plus(1, ChronoUnit.HOURS), 50, 0));

        assertThat(page.totalCount()).isEqualTo(1);
    }

    @Test
    void findHistory_respectsLimitAndOffsetAndOrdersNewestFirst() {
        Instant base = Instant.now();
        for (int i = 0; i < 5; i++) {
            repository.save(message(UUID.randomUUID().toString(), base.plusSeconds(i), "Fruit Interface", "Orange", null));
        }

        HistoryPage firstPage = repository.findHistory(new HistoryQuery(null, null, false, null, null, 2, 0));
        HistoryPage secondPage = repository.findHistory(new HistoryQuery(null, null, false, null, null, 2, 2));

        assertThat(firstPage.totalCount()).isEqualTo(5);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(secondPage.items()).hasSize(2);
        assertThat(firstPage.items().get(0).observedAt()).isAfter(firstPage.items().get(1).observedAt());
        assertThat(firstPage.items().get(1).observedAt()).isAfter(secondPage.items().get(0).observedAt());
    }

    @Test
    void countByTimeBucket_groupsByMinuteHourAndDay() {
        Instant t1 = Instant.parse("2026-01-01T10:15:00Z");
        Instant t2 = Instant.parse("2026-01-01T10:15:30Z");
        Instant t3 = Instant.parse("2026-01-01T11:00:00Z");

        repository.save(message(UUID.randomUUID().toString(), t1, "Fruit Interface", "Orange", null));
        repository.save(message(UUID.randomUUID().toString(), t2, "Fruit Interface", "Orange", null));
        repository.save(message(UUID.randomUUID().toString(), t3, "Fruit Interface", "Orange", null));

        List<TimeBucketCount> byMinute = repository.countByTimeBucket(
                t1.minus(1, ChronoUnit.HOURS), t3.plus(1, ChronoUnit.HOURS), TimeBucket.MINUTE);
        List<TimeBucketCount> byHour = repository.countByTimeBucket(
                t1.minus(1, ChronoUnit.HOURS), t3.plus(1, ChronoUnit.HOURS), TimeBucket.HOUR);
        List<TimeBucketCount> byDay = repository.countByTimeBucket(
                t1.minus(1, ChronoUnit.HOURS), t3.plus(1, ChronoUnit.HOURS), TimeBucket.DAY);

        assertThat(byMinute).hasSize(2);
        assertThat(byMinute).extracting(TimeBucketCount::count).containsExactlyInAnyOrder(2L, 1L);

        assertThat(byHour).hasSize(2);
        assertThat(byHour).extracting(TimeBucketCount::count).containsExactlyInAnyOrder(2L, 1L);

        assertThat(byDay).hasSize(1);
        assertThat(byDay.get(0).count()).isEqualTo(3);
    }

    @Test
    void countByField_groupsByInterfaceNameAndMessageType() {
        Instant now = Instant.now();
        repository.save(message(UUID.randomUUID().toString(), now, "Fruit Interface", "Orange", null));
        repository.save(message(UUID.randomUUID().toString(), now, "Fruit Interface", "Banana", null));
        repository.save(message(UUID.randomUUID().toString(), now, "Weather Interface", "TemperatureReading", null));

        List<BreakdownCount> byInterface = repository.countByField(
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), GroupByField.INTERFACE_NAME);
        List<BreakdownCount> byMessageType = repository.countByField(
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), GroupByField.MESSAGE_TYPE);

        assertThat(byInterface).extracting(BreakdownCount::key).containsExactlyInAnyOrder("Fruit Interface", "Weather Interface");
        assertThat(byInterface).filteredOn(c -> c.key().equals("Fruit Interface")).extracting(BreakdownCount::count).containsExactly(2L);

        assertThat(byMessageType).hasSize(3);
    }
}
