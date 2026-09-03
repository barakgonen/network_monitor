package com.example.publisher.sample;

import com.example.publisher.send.SendRequest;

/**
 * A named, persisted {@link SendRequest} - the unit save/load and export/import operate on.
 * {@code eventsPerTimeUnit}/{@code timeUnit} are null for a one-shot sample and set for a sample
 * that was saved while configured for periodic sending; there is no separate "periodic sample"
 * type since the Send tab's periodic-rate inputs already live alongside the rest of the form.
 */
public record SavedSample(
        String name,
        SendRequest sendRequest,
        Integer eventsPerTimeUnit,
        String timeUnit
) {
}
