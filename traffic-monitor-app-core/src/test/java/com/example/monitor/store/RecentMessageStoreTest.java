package com.example.monitor.store;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.model.ObservedMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RecentMessageStoreTest {

    @Test
    void add_singleMessage_appearsInRecent() {
        RecentMessageStore store = newStore(500);
        ObservedMessage message = observedMessage("a");

        store.add(message);

        assertThat(store.recent()).containsExactly(message);
    }

    @Test
    void add_multipleMessages_recentReturnsNewestFirst() {
        RecentMessageStore store = newStore(500);
        ObservedMessage a = observedMessage("a");
        ObservedMessage b = observedMessage("b");
        ObservedMessage c = observedMessage("c");

        store.add(a);
        store.add(b);
        store.add(c);

        assertThat(store.recent()).containsExactly(c, b, a);
    }

    @Test
    void add_beyondMaxSize_evictsOldestMessages() {
        RecentMessageStore store = newStore(3);

        for (int i = 0; i < 5; i++) {
            store.add(observedMessage("msg-" + i));
        }

        List<ObservedMessage> recent = store.recent();
        assertThat(recent).hasSize(3);
        assertThat(recent).extracting(ObservedMessage::id).containsExactly("msg-4", "msg-3", "msg-2");
    }

    @Test
    void recent_returnsDefensiveCopy() {
        RecentMessageStore store = newStore(500);
        store.add(observedMessage("a"));

        List<ObservedMessage> first = store.recent();
        first.clear();

        assertThat(store.recent()).hasSize(1);
    }

    @Test
    void add_concurrentlyFromMultipleThreads_doesNotLoseMessagesOrCorruptState() throws InterruptedException {
        int maxSize = 100;
        RecentMessageStore store = newStore(maxSize);

        int threadCount = 20;
        int addsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            int threadIndex = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < addsPerThread; i++) {
                        store.add(observedMessage("t" + threadIndex + "-" + i));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        List<ObservedMessage> recent = store.recent();
        assertThat(recent).hasSize(maxSize);
        assertThat(recent).doesNotContainNull();
        assertThat(recent).doesNotHaveDuplicates();
    }

    private static RecentMessageStore newStore(int maxSize) {
        TrafficMonitorProperties properties = new TrafficMonitorProperties();
        properties.getStore().setMaxSize(maxSize);
        return new RecentMessageStore(properties);
    }

    private static ObservedMessage observedMessage(String id) {
        return new ObservedMessage(
                id,
                Instant.now(),
                "UDP",
                "127.0.0.1:1234",
                5001,
                "Fruit Interface",
                "Orange",
                Map.of(),
                Map.of(),
                0,
                "",
                "",
                null
        );
    }
}
