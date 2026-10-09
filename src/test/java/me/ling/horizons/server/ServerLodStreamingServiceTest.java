package me.ling.horizons.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void coarseLodStartsAtPlayerAndExpandsOutward() {
        var columns = ServerLodStreamingService.orderedColumns(-94, -17, 64, 4);

        assertEquals(-3, columns.getFirst().secX());
        assertEquals(-1, columns.getFirst().secZ());
        for (int i = 1; i < columns.size(); i++) {
            assertTrue(columns.get(i - 1).distSq() <= columns.get(i).distSq());
        }
    }
}
