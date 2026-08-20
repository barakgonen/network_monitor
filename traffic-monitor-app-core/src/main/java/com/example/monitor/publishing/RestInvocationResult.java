package com.example.monitor.publishing;

import java.util.List;
import java.util.Map;

/** The outcome of one {@link RestOperationInvoker} call - {@code parseError} is set only when the HTTP call itself failed (connect/timeout/etc), not on a non-2xx status. */
public record RestInvocationResult(int statusCode, Map<String, List<String>> headers, byte[] bodyBytes, String parseError) {
}
