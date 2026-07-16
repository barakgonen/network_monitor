package com.example.monitor.api;

import java.util.List;

public record TimeSeriesResponse(String bucket, List<TimeSeriesPoint> points) {
}
