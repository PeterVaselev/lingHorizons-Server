package me.ling.horizons.commonImpl;

import me.ling.horizons.common.Logger;
import me.ling.horizons.common.config.Serialization;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Common initialization for LING Horizons Server on NeoForge.
 */
public class LingCommon {
    public static final String MOD_VERSION;
    public static final boolean IS_DEDICATED_SERVER;
    public static final boolean IS_IN_MINECRAFT;

    static {
        // Use LoadingModList for early access
        var loadingList = LoadingModList.get();
        var modFile = loadingList != null ? 
                (loadingList.getModFileById("ling_horizons_server") != null ? 
                        loadingList.getModFileById("ling_horizons_server") : 
                        loadingList.getModFileById("ling_horizons")) : null;

        if (modFile == null) {
            IS_IN_MINECRAFT = false;
            Logger.info("Initializing LING Horizons Server Standalone");
            MOD_VERSION = "1.0.0";
            IS_DEDICATED_SERVER = true;
        } else {
            IS_IN_MINECRAFT = true;
            var version = modFile.getMods().stream()
                    .filter(m -> m.getModId().equals("ling_horizons_server") || m.getModId().equals("ling_horizons"))
                    .findFirst()
                    .map(m -> m.getVersion().toString())
                    .orElse("1.0.0");
            MOD_VERSION = version;
            IS_DEDICATED_SERVER = FMLLoader.getDist() == Dist.DEDICATED_SERVER;
        }

        // Always ensure config serialization is fully initialized
        Serialization.init();
    }

    public static boolean isVerificationFlagOn(String name) {
        return isVerificationFlagOn(name, false);
    }

    public static boolean isVerificationFlagOn(String name, boolean defaultOn) {
        return System.getProperty("voxy." + name, defaultOn ? "true" : "false").equals("true");
    }

    public static void breakpoint() {
        int breakpoint = 0;
    }

    public interface IInstanceFactory { LingInstance create(); }
    private static LingInstance INSTANCE;
    private static IInstanceFactory FACTORY = null;

    public static void setInstanceFactory(IInstanceFactory factory) {
        if (FACTORY != null) {
            throw new IllegalStateException("Cannot set instance factory more than once");
        }
        FACTORY = factory;
    }

    public static LingInstance getInstance() {
        return INSTANCE;
    }

    public static void shutdownInstance() {
        if (INSTANCE != null) {
            var instance = INSTANCE;
            INSTANCE = null;
            instance.shutdown();
        }
    }

    public static void createInstance() {
        if (FACTORY == null) {
            return;
        }
        if (INSTANCE != null) {
            throw new IllegalStateException("Cannot create multiple instances");
        }
        INSTANCE = FACTORY.create();
    }

    public static boolean isAvailable() {
        return FACTORY != null;
    }

    public static final boolean IS_MINE_IN_ABYSS = false;
}