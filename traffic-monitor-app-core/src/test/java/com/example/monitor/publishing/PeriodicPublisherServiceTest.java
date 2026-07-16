package com.example.monitor.publishing;

import com.example.monitor.api.PeriodicPublishRequest;
import com.example.monitor.api.PeriodicPublishStatus;
import com.example.monitor.api.PublishRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodicPublisherServiceTest {

    @Mock
    private MonitorPayloadFactory payloadFactory;

    @Mock
    private UdpMessagePublisher udpMessagePublisher;

    @Mock
    private TcpMessagePublisher tcpMessagePublisher;

    private PeriodicPublisherService service;

    @BeforeEach
    void setUp() {
        service = new PeriodicPublisherService(payloadFactory, udpMessagePublisher, tcpMessagePublisher);
    }

    @AfterEach
    void tearDown() {
        service.stop();
    }

    private static PeriodicPublishRequest fastRequest() {
        PublishRequest publishRequest = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, null, Map.of());
        return new PeriodicPublishRequest(publishRequest, 1000, "SECOND");
    }

    @Test
    void start_withValidRequest_returnsRunningStatusImmediately() {
        when(payloadFactory.create(any(), any(), any())).thenReturn(new byte[] {1});

        PeriodicPublishStatus status = service.start(fastRequest());

        assertThat(status.running()).isTrue();
        assertThat(status.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(status.messageType()).isEqualTo("Orange");
        assertThat(status.host()).isEqualTo("localhost");
        assertThat(status.port()).isEqualTo(7001);
        assertThat(status.eventsPerTimeUnit()).isEqualTo(1000);
        assertThat(status.timeUnit()).isEqualTo("SECOND");
    }

    @Test
    void start_withTcpTransport_publishesViaTcpPublisherNotUdp() {
        when(payloadFactory.create(any(), any(), any())).thenReturn(new byte[] {1});

        PublishRequest publishRequest = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, "TCP", Map.of());
        service.start(new PeriodicPublishRequest(publishRequest, 1000, "SECOND"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(service.status().sentCount()).isGreaterThan(0));

        org.mockito.Mockito.verify(tcpMessagePublisher, org.mockito.Mockito.atLeastOnce())
                .send(any(), org.mockito.ArgumentMatchers.anyInt(), any());
        org.mockito.Mockito.verifyNoInteractions(udpMessagePublisher);
    }

    @Test
    void start_thenAwaitSentCountIncreasing_confirmsSchedulerFiring() {
        when(payloadFactory.create(any(), any(), any())).thenReturn(new byte[] {1});

        service.start(fastRequest());

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(service.status().sentCount()).isGreaterThan(0));
    }

    @Test
    void start_whenCalledWhileAlreadyRunning_cancelsPreviousTaskAndStartsNew() {
        when(payloadFactory.create(any(), any(), any())).thenReturn(new byte[] {1});

        service.start(fastRequest());
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(service.status().sentCount()).isGreaterThan(0));

        PublishRequest secondPublishRequest = new PublishRequest("Weather Interface", "TemperatureReading", "otherhost", 8001, null, Map.of());
        PeriodicPublishRequest secondRequest = new PeriodicPublishRequest(secondPublishRequest, 1000, "SECOND");
        PeriodicPublishStatus status = service.start(secondRequest);

        assertThat(status.interfaceName()).isEqualTo("Weather Interface");
        assertThat(status.host()).isEqualTo("otherhost");
    }

    @Test
    void stop_afterStart_haltsFurtherSentCountIncrements() {
        when(payloadFactory.create(any(), any(), any())).thenReturn(new byte[] {1});

        service.start(fastRequest());
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(service.status().sentCount()).isGreaterThan(0));

        service.stop();
        await().pollDelay(Duration.ofMillis(100)).until(() -> true);
        long countAfterStop = service.status().sentCount();

        await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(service.status().sentCount()).isEqualTo(countAfterStop));
    }

    @Test
    void stop_whenNeverStarted_returnsNotRunningStatusWithoutError() {
        PeriodicPublishStatus status = service.stop();

        assertThat(status.running()).isFalse();
    }

    @Test
    void status_whenNeverStarted_returnsAllDefaultZeroValues() {
        PeriodicPublishStatus status = service.status();

        assertThat(status.running()).isFalse();
        assertThat(status.interfaceName()).isNull();
        assertThat(status.sentCount()).isZero();
        assertThat(status.lastError()).isNull();
    }

    @Test
    void start_withNullRequest_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.start(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_withNullPublishRequest_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.start(new PeriodicPublishRequest(null, 1, "SECOND")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_withZeroOrNegativeEventsPerTimeUnit_throwsIllegalArgumentException() {
        PublishRequest publishRequest = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, null, Map.of());

        assertThatThrownBy(() -> service.start(new PeriodicPublishRequest(publishRequest, 0, "SECOND")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(new PeriodicPublishRequest(publishRequest, -1, "SECOND")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_withUnsupportedTimeUnit_throwsIllegalArgumentException() {
        PublishRequest publishRequest = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, null, Map.of());

        assertThatThrownBy(() -> service.start(new PeriodicPublishRequest(publishRequest, 1, "DAY")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_withBlankTimeUnit_defaultsToSecond() {
        PublishRequest publishRequest = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, null, Map.of());

        PeriodicPublishStatus status = service.start(new PeriodicPublishRequest(publishRequest, 10, ""));

        assertThat(status.timeUnit()).isEqualTo("SECOND");
    }

    @Test
    void start_whenEventsPerTimeUnitTooHighForTimeUnit_throwsIllegalArgumentException() {
        PublishRequest publishRequest = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, null, Map.of());

        assertThatThrownBy(() -> service.start(new PeriodicPublishRequest(publishRequest, 1001, "SECOND")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculateIntervalMillis_forSecondMinuteHour_computesExpectedValues() {
        PublishRequest publishRequest = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, null, Map.of());

        assertThat(service.start(new PeriodicPublishRequest(publishRequest, 10, "SECOND")).intervalMillis()).isEqualTo(100);
        assertThat(service.start(new PeriodicPublishRequest(publishRequest, 60, "MINUTE")).intervalMillis()).isEqualTo(1000);
        assertThat(service.start(new PeriodicPublishRequest(publishRequest, 3600, "HOUR")).intervalMillis()).isEqualTo(1000);
    }

    @Test
    void publishOnce_whenPayloadFactoryThrows_setsLastErrorAndContinuesSchedule() {
        when(payloadFactory.create(any(), any(), any())).thenThrow(new IllegalArgumentException("boom"));

        service.start(fastRequest());

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(service.status().lastError()).isEqualTo("boom"));

        long errorCountAtFirstCheck = service.status().sentCount();
        assertThat(errorCountAtFirstCheck).isZero();

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(service.status().lastError()).isNotNull());
    }
}
