package com.example.monitor.api;

import com.example.monitor.persistence.BreakdownCount;
import com.example.monitor.persistence.GroupByField;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.persistence.TimeBucket;
import com.example.monitor.persistence.TimeBucketCount;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@RestController
public class AnalyticsController {
    private final MessageArchiveRepository messageArchiveRepository;

    public AnalyticsController(MessageArchiveRepository messageArchiveRepository) {
        this.messageArchiveRepository = messageArchiveRepository;
    }

    @GetMapping("/api/analytics/timeseries")
    public TimeSeriesResponse timeseries(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "bucket", defaultValue = "hour") String bucket
    ) {
        Instant toInstant = parseInstantOrDefault(to, Instant.now());
        Instant fromInstant = parseInstantOrDefault(from, toInstant.minus(Duration.ofHours(24)));
        TimeBucket timeBucket = parseBucket(bucket);

        List<TimeBucketCount> counts = messageArchiveRepository.countByTimeBucket(fromInstant, toInstant, timeBucket);
        List<TimeSeriesPoint> points = counts.stream()
                .map(c -> new TimeSeriesPoint(c.bucketStart(), c.count()))
                .toList();

        return new TimeSeriesResponse(timeBucket.name().toLowerCase(Locale.ROOT), points);
    }

    @GetMapping("/api/analytics/breakdown")
    public BreakdownResponse breakdown(
            @RequestParam(name = "groupBy") String groupBy,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ) {
        Instant toInstant = parseInstantOrDefault(to, Instant.now());
        Instant fromInstant = parseInstantOrDefault(from, Instant.EPOCH);
        GroupByField field = parseGroupByField(groupBy);

        List<BreakdownCount> counts = messageArchiveRepository.countByField(fromInstant, toInstant, field);
        List<BreakdownEntry> entries = counts.stream()
                .map(c -> new BreakdownEntry(c.key(), c.count()))
                .toList();

        return new BreakdownResponse(groupBy, entries);
    }

    private Instant parseInstantOrDefault(String value, Instant defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid instant: '" + value + "'");
        }
    }

    private TimeBucket parseBucket(String bucket) {
        try {
            return TimeBucket.valueOf(bucket.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid bucket: must be one of minute, hour, day; got '" + bucket + "'");
        }
    }

    private GroupByField parseGroupByField(String groupBy) {
        if ("interfaceName".equals(groupBy)) {
            return GroupByField.INTERFACE_NAME;
        }
        if ("messageType".equals(groupBy)) {
            return GroupByField.MESSAGE_TYPE;
        }
        throw new IllegalArgumentException("Invalid groupBy: must be one of interfaceName, messageType; got '" + groupBy + "'");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
