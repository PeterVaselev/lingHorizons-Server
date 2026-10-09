package me.ling.horizons.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerLodStreamingServiceTest {
    @Test
    void limiterChargesEachPacketBeforeSending() throws InterruptedException {
        var limiter = new ServerLodStreamingService.BandwidthLimiter();
        long start = System.nanoTime();

        limiter.await(5_120, 51_200);
        limiter.await(5_120, 51_200);

        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMillis >= 190, "10 KB at 50 KB/s must take about 200 ms");
    }
}
