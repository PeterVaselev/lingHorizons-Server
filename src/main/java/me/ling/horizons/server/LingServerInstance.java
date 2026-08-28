package me.ling.horizons.server;

import me.ling.horizons.common.StorageConfigUtil;
import me.ling.horizons.common.config.ConfigBuildCtx;
import me.ling.horizons.common.config.section.SectionStorage;
import me.ling.horizons.common.config.section.SectionStorageConfig;
import me.ling.horizons.commonImpl.ImportManager;
import me.ling.horizons.commonImpl.LingInstance;
import me.ling.horizons.commonImpl.WorldIdentifier;
import me.ling.horizons.server.config.LingServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * Dedicated Server instance for LING Horizons.
 * Manages the server's Master RocksDB database and coordinates LOD streaming.
 */
public class LingServerInstance extends LingInstance {
    private final MinecraftServer server;
    private final Path basePath;
    private final SectionStorageConfig storageConfig;
    private final ServerLodStreamingService streamingService;

    private volatile boolean isGameplayMode = false;

    public LingServerInstance(MinecraftServer server) {
        super();
        this.server = server;
        this.basePath = server.getWorldPath(LevelResource.ROOT).resolve("ling_horizons");
        this.storageConfig = StorageConfigUtil.getCreateStorageConfig(
                Config.class,
                c -> c.version == 1 && c.sectionStorageConfig != null,
                () -> DEFAULT_STORAGE_CONFIG,
                this.basePath
        ).sectionStorageConfig;

        this.streamingService = new ServerLodStreamingService(this);
        this.updateDedicatedThreads();
    }

    /**
     * Dynamically updates thread allocation and workload priority based on player presence.
     */
    public void updateAdaptiveMode(int playerCount) {
        if (!LingServerConfig.adaptiveWorkloadEnabled) {
            return;
        }

        boolean newGameplayMode = playerCount > 0;
        if (newGameplayMode != this.isGameplayMode) {
            this.isGameplayMode = newGameplayMode;
            if (newGameplayMode) {
                int threads = Math.max(1, LingServerConfig.streamingWorkerThreads);
                this.setNumThreads(threads);
                me.ling.horizons.common.Logger.info("[LING Horizons Server] Switching to ACTIVE GAMEPLAY MODE (" + playerCount + " players online - Prioritizing LOD streaming & TPS stability)");
            } else {
                int threads = Math.max(LingServerConfig.streamingWorkerThreads, LingServerConfig.offlineBoostThreads);
                this.setNumThreads(threads);
                me.ling.horizons.common.Logger.info("[LING Horizons Server] Switching to OFFLINE PREGENERATION BOOST MODE (0 players - Accelerated background ingestion & auto-compaction)");
            }
        }
    }

    @Override
    public void updateDedicatedThreads() {
        int threads = (this.isGameplayMode || !LingServerConfig.adaptiveWorkloadEnabled)
                ? LingServerConfig.streamingWorkerThreads
                : Math.max(LingServerConfig.streamingWorkerThreads, LingServerConfig.offlineBoostThreads);
        this.setNumThreads(threads);
    }

    @Override
    protected ImportManager createImportManager() {
        return new ImportManager() {};
    }

    @Override
    protected SectionStorage createStorage(WorldIdentifier identifier) {
        var ctx = new ConfigBuildCtx();
        ctx.setProperty(ConfigBuildCtx.BASE_SAVE_PATH, this.basePath.toString());
        ctx.setProperty(ConfigBuildCtx.WORLD_IDENTIFIER, identifier.getWorldId());
        ctx.pushPath(ConfigBuildCtx.DEFAULT_STORAGE_PATH);
        return this.storageConfig.build(ctx);
    }

    @Override
    public boolean isIngestEnabled(WorldIdentifier worldId) {
        return true;
    }

    public ServerLodStreamingService getStreamingService() {
        return this.streamingService;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    @Override
    public void shutdown() {
        if (this.streamingService != null) {
            this.streamingService.shutdown();
        }
        super.shutdown();
    }

    private static class Config {
        public int version = 1;
        public SectionStorageConfig sectionStorageConfig;
    }

    private static final Config DEFAULT_STORAGE_CONFIG;
    static {
        var config = new Config();
        config.sectionStorageConfig = StorageConfigUtil.createDefaultSerializer();
        DEFAULT_STORAGE_CONFIG = config;
    }
}
