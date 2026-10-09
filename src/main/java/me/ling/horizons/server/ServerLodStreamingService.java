package me.ling.horizons.server;

import me.ling.horizons.common.Logger;
import me.ling.horizons.common.world.SaveLoadSystem3;
import me.ling.horizons.common.world.WorldEngine;
import me.ling.horizons.common.world.WorldSection;
import me.ling.horizons.commonImpl.WorldIdentifier;
import me.ling.horizons.server.config.LingServerConfig;
import me.ling.horizons.server.network.payload.LingHelloPayload;
import me.ling.horizons.server.network.payload.LingLodDataPayload;
import me.ling.horizons.server.network.payload.LingLodRequestPayload;
import me.ling.horizons.common.util.UnsafeUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * High-performance Asynchronous Server-to-Client LOD Streaming Service.
 * Streams pre-generated Voxel DAG LOD sections to connected clients on demand.
 */
public class ServerLodStreamingService {
    private final LingServerInstance serverInstance;
    private final ExecutorService streamingExecutor;

    private final ConcurrentHashMap<UUID, StreamState> playerStreams = new ConcurrentHashMap<>();

    private static final class StreamState {
        private final ResourceKey<Level> dimension;
        private final Set<Long> sentSections = ConcurrentHashMap.newKeySet();
        private final BandwidthLimiter bandwidth = new BandwidthLimiter();
        private Future<?> task;
        private LingLodRequestPayload request;
        private long generation;

        private StreamState(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    /** Charges the complete serialized payload before it enters the network queue. */
    static final class BandwidthLimiter {
        private long nextSendNanos;

        synchronized void await(int bytes, long bytesPerSecond) throws InterruptedException {
            long now = System.nanoTime();
            long duration = (bytes * 1_000_000_000L + bytesPerSecond - 1) / bytesPerSecond;
            long deadline = Math.max(now, nextSendNanos) + duration;
            while ((now = System.nanoTime()) < deadline) {
                TimeUnit.NANOSECONDS.sleep(deadline - now);
            }
            nextSendNanos = deadline;
        }
    }

    public ServerLodStreamingService(LingServerInstance serverInstance) {
        this.serverInstance = serverInstance;
        int threads = Math.max(1, LingServerConfig.streamingWorkerThreads);
        this.streamingExecutor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "LING Server LOD Streamer");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    /**
     * Sends the handshake payload when a player joins.
     */
    public void onPlayerJoin(ServerPlayer player) {
        if (!LingServerConfig.streamingEnabled) {
            return;
        }
        player.connection.send(new LingHelloPayload(
                1,
                LingServerConfig.maxStreamingRadiusChunks,
                true
        ));
    }

    public void onPlayerLeave(ServerPlayer player) {
        if (player != null) {
            StreamState state = this.playerStreams.remove(player.getUUID());
            if (state != null) {
                synchronized (state) {
                    if (state.task != null) state.task.cancel(true);
                }
            }
        }
    }

    /**
     * Helper record representing a 2D section position with its distance squared to player center.
     */
    private record Section2DPos(int secX, int secZ, long distSq) {}

    /**
     * Handles an asynchronous LOD chunk request from a client.
     * Implements Hierarchical Radial Streaming (Coarse Horizon First, Fine Detail Near-Player Second)
     * with per-player bandwidth throttling.
     */
    public void handleRequest(ServerPlayer player, LingLodRequestPayload request) {
        if (!LingServerConfig.streamingEnabled || player == null || player.level() == null) {
            return;
        }

        UUID playerUUID = player.getUUID();

        StreamState state = this.playerStreams.compute(playerUUID, (id, existing) -> {
            if (existing == null) return new StreamState(player.level().dimension());
            if (existing.dimension.equals(player.level().dimension())) return existing;
            synchronized (existing) {
                if (existing.task != null) existing.task.cancel(true);
            }
            return new StreamState(player.level().dimension());
        });
        synchronized (state) {
            if (state.task != null && !state.task.isDone()) {
                if (request.equals(state.request)) return;
                state.task.cancel(true);
            }
            state.request = request;
            long generation = ++state.generation;
            state.task = this.streamingExecutor.submit(() -> streamRequest(player, request, state, generation));
        }
    }

    private void streamRequest(ServerPlayer player, LingLodRequestPayload request, StreamState state, long generation) {
        WorldEngine engine = null;
        boolean engineHeld = false;
        try {
                if (Thread.currentThread().isInterrupted() || player.hasDisconnected()) {
                    return;
                }

                var level = player.serverLevel();
                engine = WorldIdentifier.ofEngine(level);
                if (engine == null) {
                    Logger.warn("[LING LOD Streamer] Could not obtain WorldEngine for level " + level.dimension().location());
                    return;
                }
                engine.acquireRef();
                engineHeld = true;

                int maxRadius = Math.min(request.radiusChunks(), LingServerConfig.maxStreamingRadiusChunks);
                int centerX = request.centerChunkX();
                int centerZ = request.centerChunkZ();
                int minLOD = Math.max(0, request.minDetailLevel());
                int maxLOD = Math.min(4, request.maxDetailLevel());

                long streamStartTime = System.currentTimeMillis();
                long totalStreamedBytes = 0;
                int totalStreamedSections = 0;

                // -------------------------------------------------------------
                // 1. PHASE 1: Immediate Distant Horizon Delivery (Coarse LOD 4 & 3)
                // Ensures player sees mountains and full horizon instantly (0 visual void holes)
                // -------------------------------------------------------------
                for (int lod = maxLOD; lod >= Math.max(3, minLOD); lod--) {
                    int minSecX = (centerX - maxRadius) >> (1 + lod);
                    int maxSecX = (centerX + maxRadius) >> (1 + lod);
                    int minSecZ = (centerZ - maxRadius) >> (1 + lod);
                    int maxSecZ = (centerZ + maxRadius) >> (1 + lod);

                    for (int secX = minSecX; secX <= maxSecX; secX++) {
                        for (int secZ = minSecZ; secZ <= maxSecZ; secZ++) {
                            if (Thread.currentThread().isInterrupted() || player.hasDisconnected()) return;

                            StreamResult res = streamColumnSections(engine, player, state, lod, secX, secZ);
                            totalStreamedBytes += res.bytes();
                            totalStreamedSections += res.sections();
                        }
                    }
                }

                // -------------------------------------------------------------
                // 2. PHASE 2: Concentric Radial Streaming for Fine Details (LOD 2, 1, 0)
                // Stream closest detailed terrain in front of the player first!
                // -------------------------------------------------------------
                for (int lod = Math.min(2, maxLOD); lod >= minLOD; lod--) {
                    if (Thread.currentThread().isInterrupted() || player.hasDisconnected()) return;

                    int minSecX = (centerX - maxRadius) >> (1 + lod);
                    int maxSecX = (centerX + maxRadius) >> (1 + lod);
                    int minSecZ = (centerZ - maxRadius) >> (1 + lod);
                    int maxSecZ = (centerZ + maxRadius) >> (1 + lod);

                    int centerSecX = centerX >> (1 + lod);
                    int centerSecZ = centerZ >> (1 + lod);

                    List<Section2DPos> positions = new ArrayList<>();
                    for (int secX = minSecX; secX <= maxSecX; secX++) {
                        for (int secZ = minSecZ; secZ <= maxSecZ; secZ++) {
                            long dx = secX - centerSecX;
                            long dz = secZ - centerSecZ;
                            long distSq = dx * dx + dz * dz;
                            positions.add(new Section2DPos(secX, secZ, distSq));
                        }
                    }

                    // Sort closest to player center first
                    positions.sort(Comparator.comparingLong(Section2DPos::distSq));

                    for (Section2DPos pos : positions) {
                        if (Thread.currentThread().isInterrupted() || player.hasDisconnected()) return;

                        StreamResult res = streamColumnSections(engine, player, state, lod, pos.secX, pos.secZ);
                        totalStreamedBytes += res.bytes();
                        totalStreamedSections += res.sections();

                    }
                }

                long totalTime = System.currentTimeMillis() - streamStartTime;
                if (totalStreamedSections > 0) {
                    Logger.info(String.format("[LING LOD Streamer] Streamed %d sections (%.1f KB) to %s in %.2fs",
                            totalStreamedSections, totalStreamedBytes / 1024.0, player.getName().getString(), totalTime / 1000.0));
                } else {
                    Logger.info("[LING LOD Streamer] Player " + player.getName().getString() + " requested LODs, but 0 sections were stored in RocksDB for this area.");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Logger.error("Error streaming LOD data to player " + player.getName().getString(), e);
            } finally {
                if (engineHeld) engine.releaseRef();
                synchronized (state) {
                    if (state.generation == generation) state.task = null;
                }
            }
    }

    private record StreamResult(long bytes, int sections) {}

    /**
     * Streams vertical sections from Y=-2 to 10 for a given (lod, secX, secZ) column.
     * Returns total bytes and sections sent.
     */
    private StreamResult streamColumnSections(WorldEngine engine, ServerPlayer player, StreamState state, int lod, int secX, int secZ) throws InterruptedException {
        long totalBytes = 0;
        int totalSections = 0;
        for (int secY = -2; secY <= 10; secY++) {
            if (Thread.currentThread().isInterrupted() || player.hasDisconnected()) break;
            long key = WorldEngine.getWorldSectionId(lod, secX, secY, secZ);
            if (state.sentSections.contains(key)) continue;
            WorldSection section = engine.acquireIfExists(lod, secX, secY, secZ);
            if (section != null) {
                byte[] data = null;
                try {
                    var mem = SaveLoadSystem3.serialize(section);
                    if (mem != null && mem.size > 0) {
                        try {
                            data = new byte[(int) mem.size];
                            UnsafeUtil.memcpy(mem.address, (int) mem.size, data);
                        } finally {
                            mem.free();
                        }
                    }
                } finally {
                    section.release();
                }
                if (data != null) {
                    state.bandwidth.await(data.length, Math.max(10, LingServerConfig.maxBandwidthPerPlayerKB) * 1024L);
                    if (Thread.currentThread().isInterrupted() || player.hasDisconnected()) break;
                    player.connection.send(new LingLodDataPayload(key, lod, data));
                    state.sentSections.add(key);
                    totalBytes += data.length;
                    totalSections++;
                }
            }
        }
        return new StreamResult(totalBytes, totalSections);
    }

    public void shutdown() {
        for (StreamState state : this.playerStreams.values()) {
            synchronized (state) {
                if (state.task != null) state.task.cancel(true);
            }
        }
        this.playerStreams.clear();
        this.streamingExecutor.shutdownNow();
    }
}
