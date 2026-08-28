package me.ling.horizons.server;

import me.ling.horizons.common.Logger;
import me.ling.horizons.server.config.LingServerConfig;
import me.ling.horizons.server.network.ServerNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Main Dedicated Server mod class for LING Horizons.
 */
@Mod("ling_horizons_server")
public class LingHorizonsServer {

    public LingHorizonsServer(IEventBus modEventBus, ModContainer container) {
        Logger.info("Initializing LING Horizons Dedicated Server Engine...");
        LingServerConfig.register(container);
        modEventBus.addListener(ServerNetworking::registerPayloads);
    }
}
