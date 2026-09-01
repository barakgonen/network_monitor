package com.example.publisher.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copied from {@code com.example.schemacore.reflect.FlattenedFieldPathUtil}
 * (traffic-monitor-app-core) rather than depended on, so this app has zero dependency on
 * traffic-monitor-app/-core - see CLAUDE.md's "Publishing lives in sample-publisher-app, not
 * here". Pure {@code Map}/{@code String} manipulation with no reflection dependency despite the
 * original's package name; shared here by {@link com.example.publisher.rest.RestRequestBodyAssembler}
 * and {@link com.example.publisher.serdes.SerdesRequestBodyAssembler}.
 *
 * <p>Regroups dotted/indexed flattened field paths (e.g. {@code "header.msgType"},
 * {@code "trackData[0].id"}) back into a nested {@code Map}/{@code Map<Integer,Object>} tree, one
 * level per call.
 */
public final class FlattenedFieldPathUtil {
    private static final Pattern PATH_SEGMENT = Pattern.compile("^([^.\\[]+)(?:\\[(\\d+)])?(?:\\.(.+))?$");

    private FlattenedFieldPathUtil() {
    }

    public static Map<String, Object> unflatten(Map<String, Object> flat) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            Matcher matcher = PATH_SEGMENT.matcher(entry.getKey());
            if (!matcher.matches()) {
                result.put(entry.getKey(), entry.getValue());
                continue;
            }

            String head = matcher.group(1);
            String index = matcher.group(2);
            String tail = matcher.group(3);

            if (index != null) {
                @SuppressWarnings("unchecked")
                Map<Integer, Object> indexed = (Map<Integer, Object>) result.computeIfAbsent(head, k -> new TreeMap<Integer, Object>());
                putIndexed(indexed, Integer.parseInt(index), tail, entry.getValue());
                continue;
            }

            if (tail == null) {
                result.put(head, entry.getValue());
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> child = (Map<String, Object>) result.computeIfAbsent(head, k -> new LinkedHashMap<String, Object>());
            child.put(tail, entry.getValue());
        }
        return result;
    }

    private static void putIndexed(Map<Integer, Object> indexed, int index, String tail, Object value) {
        if (tail == null) {
            indexed.put(index, value);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) indexed.computeIfAbsent(index, k -> new LinkedHashMap<String, Object>());
        item.put(tail, value);
    }
}
