package me.ling.horizons.server.event;

import me.ling.horizons.common.world.service.VoxelIngestService;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Listens for chunk load events on the dedicated server.
 * Automatically ingests active and dynamically loaded chunks into Master RocksDB LOD storage.
 */
@EventBusSubscriber(modid = "ling_horizons_server")
public class ServerChunkEventListener {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (VoxelIngestService.isShuttingDown()) {
            return;
        }
        if (event.getLevel() != null && !event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk worldChunk) {
            VoxelIngestService.tryAutoIngestChunk(worldChunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (VoxelIngestService.isShuttingDown()) {
            return;
        }
        if (event.getLevel() != null && !event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk worldChunk) {
            VoxelIngestService.tryAutoIngestChunk(worldChunk);
        }
    }

    @SubscribeEvent
    public static void onChunkWatch(net.neoforged.neoforge.event.level.ChunkWatchEvent.Watch event) {
        if (VoxelIngestService.isShuttingDown()) {
            return;
        }
        var level = event.getLevel();
        if (level != null) {
            var chunk = level.getChunkSource().getChunkNow(event.getPos().x, event.getPos().z);
            if (chunk != null) {
                VoxelIngestService.tryAutoIngestChunk(chunk);
            }
        }
    }
}
