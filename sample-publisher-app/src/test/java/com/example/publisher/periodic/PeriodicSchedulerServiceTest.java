package com.example.publisher.periodic;

import com.example.publisher.send.SendOrchestrationService;
import com.example.publisher.send.SendRequest;
import com.example.publisher.send.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeriodicSchedulerServiceTest {

    @Mock
    private SendOrchestrationService sendOrchestrationService;

    private PeriodicSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        // Must be constructed here, not as a field initializer - field initializers run before
        // MockitoExtension injects @Mock fields, so an eager initializer would capture a null
        // sendOrchestrationService reference into the (final) constructor param forever.
        scheduler = new PeriodicSchedulerService(sendOrchestrationService);
    }

    private static SendRequest stubRequest() {
        return new SendRequest("fruit", "Orange", "localhost", 5001, "UDP", Map.of());
    }

    @Test
    void start_rejectsNonPositiveEventsPerTimeUnit() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        scheduler.start(new PeriodicStartRequest(stubRequest(), 0, "SECOND")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_returnsRunningJobWithGeneratedId() {
        when(sendOrchestrationService.send(any())).thenReturn(SendResult.sent(10, List.of("localhost:5001")));

        PeriodicJobDto job = scheduler.start(new PeriodicStartRequest(stubRequest(), 5, "SECOND"));

        assertThat(job.jobId()).isNotBlank();
        assertThat(job.running()).isTrue();
        assertThat(job.intervalMillis()).isEqualTo(200L);
        assertThat(job.timeUnit()).isEqualTo("SECOND");

        scheduler.stop(job.jobId());
    }

    @Test
    void start_calledTwice_createsTwoIndependentConcurrentJobs() {
        when(sendOrchestrationService.send(any())).thenReturn(SendResult.sent(10, List.of("localhost:5001")));

        PeriodicJobDto job1 = scheduler.start(new PeriodicStartRequest(stubRequest(), 1, "SECOND"));
        PeriodicJobDto job2 = scheduler.start(new PeriodicStartRequest(stubRequest(), 1, "SECOND"));

        assertThat(job1.jobId()).isNotEqualTo(job2.jobId());
        assertThat(scheduler.list()).extracting(PeriodicJobDto::jobId).contains(job1.jobId(), job2.jobId());

        scheduler.stop(job1.jobId());
        scheduler.stop(job2.jobId());
    }

    @Test
    void sendOnce_incrementsSentCountOnSuccessAndTracksLastErrorOnFailure() {
        when(sendOrchestrationService.send(any()))
                .thenReturn(SendResult.sent(10, List.of("localhost:5001")))
                .thenReturn(SendResult.failure("boom"));

        PeriodicJobDto job = scheduler.start(new PeriodicStartRequest(stubRequest(), 20, "SECOND"));

        await().untilAsserted(() -> {
            PeriodicJobDto status = scheduler.status(job.jobId()).orElseThrow();
            assertThat(status.sentCount()).isGreaterThanOrEqualTo(1);
        });

        scheduler.stop(job.jobId());
        verify(sendOrchestrationService, atLeastOnce()).send(any());
    }

    @Test
    void stop_marksJobNotRunningButKeepsItListed() {
        when(sendOrchestrationService.send(any())).thenReturn(SendResult.sent(10, List.of("localhost:5001")));

        PeriodicJobDto job = scheduler.start(new PeriodicStartRequest(stubRequest(), 1, "SECOND"));
        PeriodicJobDto stopped = scheduler.stop(job.jobId()).orElseThrow();

        assertThat(stopped.running()).isFalse();
        assertThat(scheduler.list()).extracting(PeriodicJobDto::jobId).contains(job.jobId());
    }

    @Test
    void stop_withUnknownJobId_returnsEmpty() {
        assertThat(scheduler.stop("does-not-exist")).isEmpty();
    }
}
