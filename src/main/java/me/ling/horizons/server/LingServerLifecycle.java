package me.ling.horizons.server;

import me.ling.horizons.common.Logger;
import me.ling.horizons.commonImpl.LingCommon;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Manages dedicated server lifecycle events and player connections.
 */
@EventBusSubscriber(modid = "ling_horizons_server")
public class LingServerLifecycle {

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Logger.info("Starting LING Horizons Master Server Engine...");
        me.ling.horizons.common.world.service.VoxelIngestService.setShuttingDown(false);
        var server = event.getServer();
        if (LingCommon.getInstance() != null) {
            LingCommon.shutdownInstance();
        }
        LingCommon.setInstanceFactory(() -> new LingServerInstance(server));
        LingCommon.createInstance();
        var instance = LingCommon.getInstance();
        if (instance instanceof LingServerInstance serverInstance) {
            serverInstance.updateAdaptiveMode(0);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        me.ling.horizons.server.integration.ChunkyIntegration.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var instance = LingCommon.getInstance();
            if (instance instanceof LingServerInstance serverInstance) {
                serverInstance.getStreamingService().onPlayerJoin(player);
                int count = player.getServer() != null ? player.getServer().getPlayerCount() : 1;
                serverInstance.updateAdaptiveMode(count);
                if (player.getServer() != null) {
                    me.ling.horizons.server.integration.ChunkyIntegration.onPlayerCountChanged(player.getServer(), count);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var instance = LingCommon.getInstance();
            if (instance instanceof LingServerInstance serverInstance) {
                serverInstance.getStreamingService().onPlayerLeave(player);
                int count = player.getServer() != null ? Math.max(0, player.getServer().getPlayerCount() - 1) : 0;
                serverInstance.updateAdaptiveMode(count);
                if (player.getServer() != null) {
                    me.ling.horizons.server.integration.ChunkyIntegration.onPlayerCountChanged(player.getServer(), count);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Logger.info("Server stopping initiated: halting Chunky and shutting down LING Horizons Master Engine...");
        var server = event.getServer();
        if (server != null) {
            me.ling.horizons.server.integration.ChunkyIntegration.onServerStopping(server);
        }
        me.ling.horizons.common.world.service.VoxelIngestService.setShuttingDown(true);
        LingCommon.shutdownInstance();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Logger.info("Stopping LING Horizons Master Server Engine (final cleanup)...");
        me.ling.horizons.common.world.service.VoxelIngestService.setShuttingDown(true);
        LingCommon.shutdownInstance();
    }
}
