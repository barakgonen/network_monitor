package com.example.monitor.api;

import java.time.Instant;

public record TimeSeriesPoint(Instant bucketStart, long count) {
}
