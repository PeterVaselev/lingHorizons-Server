package me.ling.horizons.server;

import me.ling.horizons.common.Logger;
import me.ling.horizons.commonImpl.LingCommon;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Manages dedicated server lifecycle events and player connections.
 */
@EventBusSubscriber(modid = "ling_horizons_server")
public class LingServerLifecycle {

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Logger.info("Starting LING Horizons Master Server Engine...");
        var server = event.getServer();
        if (LingCommon.getInstance() != null) {
            LingCommon.shutdownInstance();
        }
        LingCommon.setInstanceFactory(() -> new LingServerInstance(server));
        LingCommon.createInstance();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var instance = LingCommon.getInstance();
            if (instance instanceof LingServerInstance serverInstance) {
                serverInstance.getStreamingService().onPlayerJoin(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var instance = LingCommon.getInstance();
            if (instance instanceof LingServerInstance serverInstance) {
                serverInstance.getStreamingService().onPlayerLeave(player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Logger.info("Stopping LING Horizons Master Server Engine...");
        LingCommon.shutdownInstance();
    }
}
