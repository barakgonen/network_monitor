package com.example.monitor.persistence;

import java.time.Instant;

public record TimeBucketCount(Instant bucketStart, long count) {
}
