package me.ling.horizons.server.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration for LING Horizons LOD Streaming.
 */
@EventBusSubscriber(modid = "ling_horizons_server", bus = EventBusSubscriber.Bus.MOD)
public class LingServerConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue STREAMING_ENABLED = BUILDER
            .comment("Enable server-side LOD streaming to connected LING Horizons clients")
            .define("streamingEnabled", true);

    private static final ModConfigSpec.IntValue MAX_STREAMING_RADIUS_CHUNKS = BUILDER
            .comment("Maximum streaming radius in chunks around player (e.g. 128 = 2,048 blocks)")
            .defineInRange("maxStreamingRadiusChunks", 128, 16, 512);

    private static final ModConfigSpec.IntValue MAX_BANDWIDTH_PER_PLAYER_KB = BUILDER
            .comment("Maximum bandwidth per player in KB/s for LOD streaming (prevents server lag)")
            .defineInRange("maxBandwidthPerPlayerKB", 50, 10, 500);

    private static final ModConfigSpec.IntValue STREAMING_WORKER_THREADS = BUILDER
            .comment("Number of background worker threads dedicated to LOD reading and streaming")
            .defineInRange("streamingWorkerThreads", 2, 1, 8);

    private static final ModConfigSpec.BooleanValue ADAPTIVE_WORKLOAD_ENABLED = BUILDER
            .comment("Automatically adapt background workload based on player presence (Throttled during gameplay, Boosted when 0 players)")
            .define("adaptiveWorkloadEnabled", true);

    private static final ModConfigSpec.IntValue OFFLINE_BOOST_THREADS = BUILDER
            .comment("Number of threads dedicated to voxel pregeneration and storage when 0 players are online")
            .defineInRange("offlineBoostThreads", 4, 1, 16);

    private static final ModConfigSpec.BooleanValue AUTO_MANAGE_CHUNKY = BUILDER
            .comment("Automatically pause Chunky when players are online and resume Chunky when 0 players")
            .define("autoManageChunky", true);

    private static final ModConfigSpec.BooleanValue SILENT_CHUNKY_BRIDGE_LOGS = BUILDER
            .comment("Silence Chunky pause/resume notification messages in server console")
            .define("silentChunkyBridgeLogs", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // Cached values for fast access
    public static boolean streamingEnabled = true;
    public static int maxStreamingRadiusChunks = 128;
    public static int maxBandwidthPerPlayerKB = 50;
    public static int streamingWorkerThreads = 2;
    public static boolean adaptiveWorkloadEnabled = true;
    public static int offlineBoostThreads = 4;
    public static boolean autoManageChunky = true;
    public static boolean silentChunkyBridgeLogs = false;

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "ling_horizons-server.toml");
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC && (event instanceof ModConfigEvent.Loading || event instanceof ModConfigEvent.Reloading)) {
            streamingEnabled = STREAMING_ENABLED.get();
            maxStreamingRadiusChunks = MAX_STREAMING_RADIUS_CHUNKS.get();
            maxBandwidthPerPlayerKB = MAX_BANDWIDTH_PER_PLAYER_KB.get();
            streamingWorkerThreads = STREAMING_WORKER_THREADS.get();
            adaptiveWorkloadEnabled = ADAPTIVE_WORKLOAD_ENABLED.get();
            offlineBoostThreads = OFFLINE_BOOST_THREADS.get();
            autoManageChunky = AUTO_MANAGE_CHUNKY.get();
            silentChunkyBridgeLogs = SILENT_CHUNKY_BRIDGE_LOGS.get();
        }
    }
}
