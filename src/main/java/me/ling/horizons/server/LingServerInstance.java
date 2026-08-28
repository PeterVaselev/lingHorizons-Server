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

    @Override
    public void updateDedicatedThreads() {
        this.setNumThreads(LingServerConfig.streamingWorkerThreads);
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
