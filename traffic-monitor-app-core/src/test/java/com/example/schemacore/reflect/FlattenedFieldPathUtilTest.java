package com.example.schemacore.reflect;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class FlattenedFieldPathUtilTest {

    @Test
    void unflatten_withNoDotsOrBrackets_returnsFieldsAsIs() {
        Map<String, Object> result = FlattenedFieldPathUtil.unflatten(Map.of("name", "Rex"));

        assertThat(result).containsEntry("name", "Rex");
    }

    @Test
    void unflatten_withDottedPath_groupsIntoNestedMap() {
        Map<String, Object> result = FlattenedFieldPathUtil.unflatten(Map.of("header.msgType", "3", "header.msgCounter", "1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) result.get("header");
        assertThat(header).containsEntry("msgType", "3").containsEntry("msgCounter", "1");
    }

    @Test
    void unflatten_withNestedDottedPath_groupsRecursively() {
        Map<String, Object> result = FlattenedFieldPathUtil.unflatten(Map.of("owner.address.city", "Tel Aviv"));

        @SuppressWarnings("unchecked")
        Map<String, Object> owner = (Map<String, Object>) result.get("owner");
        // Only one level unflattens per call - the remaining "address.city" stays as a single
        // flattened key inside "owner", to be unflattened again by whatever recurses into it
        // (ReflectiveFieldApplier.build, or RestRequestBodyAssembler.coerceObject).
        assertThat(owner).containsKey("address.city");
    }

    @Test
    void unflatten_withIndexedPath_groupsByIndexIntoTreeMap() {
        Map<String, Object> result = FlattenedFieldPathUtil.unflatten(
                Map.of("tracks[0].id", "1", "tracks[2].id", "3"));

        @SuppressWarnings("unchecked")
        TreeMap<Integer, Object> tracks = (TreeMap<Integer, Object>) result.get("tracks");
        assertThat(tracks.keySet()).containsExactly(0, 2);

        @SuppressWarnings("unchecked")
        Map<String, Object> firstItem = (Map<String, Object>) tracks.get(0);
        assertThat(firstItem).containsEntry("id", "1");
    }

    @Test
    void unflatten_withIndexedPathAndNoTail_storesValueDirectlyAtIndex() {
        Map<String, Object> result = FlattenedFieldPathUtil.unflatten(Map.of("tags[0]", "a", "tags[1]", "b"));

        @SuppressWarnings("unchecked")
        TreeMap<Integer, Object> tags = (TreeMap<Integer, Object>) result.get("tags");
        assertThat(tags.get(0)).isEqualTo("a");
        assertThat(tags.get(1)).isEqualTo("b");
    }
}
