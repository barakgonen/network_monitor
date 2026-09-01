package com.example.publisher.send.io;

import java.util.List;
import java.util.Map;

/**
 * Copied from {@code com.example.monitor.publishing.RestInvocationResult} (traffic-monitor-app-core)
 * rather than depended on, so this app has zero dependency on traffic-monitor-app/-core - see
 * CLAUDE.md's "Publishing lives in sample-publisher-app, not here".
 *
 * <p>The outcome of one {@link RestOperationInvoker} call - {@code parseError} is set only when
 * the HTTP call itself failed (connect/timeout/etc), not on a non-2xx status.
 */
public record RestInvocationResult(int statusCode, Map<String, List<String>> headers, byte[] bodyBytes, String parseError) {
}
