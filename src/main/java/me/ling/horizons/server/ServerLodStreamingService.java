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
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.system.MemoryUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance Asynchronous Server-to-Client LOD Streaming Service.
 * Streams pre-generated Voxel DAG LOD sections to connected clients on demand.
 */
public class ServerLodStreamingService {
    private final LingServerInstance serverInstance;
    private final ExecutorService streamingExecutor;

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

    /**
     * Handles an asynchronous LOD chunk request from a client.
     */
    public void handleRequest(ServerPlayer player, LingLodRequestPayload request) {
        if (!LingServerConfig.streamingEnabled || player == null || player.level() == null) {
            return;
        }

        this.streamingExecutor.submit(() -> {
            try {
                var level = player.serverLevel();
                WorldEngine engine = WorldIdentifier.ofEngineNullable(level);
                if (engine == null) {
                    return;
                }

                int maxRadius = Math.min(request.radiusChunks(), LingServerConfig.maxStreamingRadiusChunks);
                int centerX = request.centerChunkX();
                int centerZ = request.centerChunkZ();

                // Stream LOD sections starting from coarser detail levels to fine details
                int minLOD = Math.max(0, request.minDetailLevel());
                int maxLOD = Math.min(4, request.maxDetailLevel());

                for (int lod = maxLOD; lod >= minLOD; lod--) {
                    int step = 1 << (lod + 1); // LOD section size in chunks (2, 4, 8, 16, 32)
                    int rad = maxRadius;

                    for (int dx = -rad; dx <= rad; dx += step) {
                        for (int dz = -rad; dz <= rad; dz += step) {
                            int cx = centerX + dx;
                            int cz = centerZ + dz;

                            // Calculate section key in engine coordinate space
                            int secX = cx >> (1 + lod);
                            int secZ = cz >> (1 + lod);

                            // Stream common vertical sections (Y=-2 to 10 for world height -64..320)
                            for (int secY = -2; secY <= 10; secY++) {
                                WorldSection section = engine.acquireIfExists(lod, secX, secY, secZ);
                                if (section != null) {
                                    try {
                                        var mem = SaveLoadSystem3.serialize(section);
                                        if (mem != null && mem.size > 0) {
                                            byte[] data = new byte[(int) mem.size];
                                            MemoryUtil.memByteBuffer(mem.address, (int) mem.size).get(data);
                                            mem.free();

                                            long key = WorldEngine.getWorldSectionId(lod, secX, secY, secZ);
                                            player.connection.send(new LingLodDataPayload(key, lod, data));
                                        }
                                    } finally {
                                        section.release();
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Logger.error("Error streaming LOD data to player " + player.getName().getString(), e);
            }
        });
    }

    public void shutdown() {
        this.streamingExecutor.shutdownNow();
    }
}
