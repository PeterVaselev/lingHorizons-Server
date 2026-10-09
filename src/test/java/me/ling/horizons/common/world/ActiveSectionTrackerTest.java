package me.ling.horizons.common.world;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ActiveSectionTrackerTest {
    @Test
    void failedLoadReleasesConcurrentAcquirers() throws Exception {
        var entered = new CountDownLatch(1);
        var continueLoad = new CountDownLatch(1);
        var loads = new AtomicInteger();
        var tracker = new ActiveSectionTracker(0, section -> {
            loads.incrementAndGet();
            entered.countDown();
            try {
                if (!continueLoad.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            throw new IllegalArgumentException("corrupt section");
        }, 8);

        var workers = Executors.newFixedThreadPool(2, task -> {
            var thread = new Thread(task, "LOD tracker regression test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            var first = workers.submit(() -> tracker.acquire(0, 0, 0, 0, false));
            if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("loader never started");
            var second = workers.submit(() -> tracker.acquire(0, 0, 0, 0, false));
            continueLoad.countDown();
            var firstSection = first.get(5, TimeUnit.SECONDS);
            var secondSection = second.get(5, TimeUnit.SECONDS);
            assertSame(firstSection, secondSection);
            assertEquals(1, loads.get());
            firstSection.release();
            secondSection.release();
        } finally {
            continueLoad.countDown();
            workers.shutdownNow();
        }
    }
}
