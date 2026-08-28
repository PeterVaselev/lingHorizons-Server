package me.ling.horizons.server.network;

import me.ling.horizons.commonImpl.LingCommon;
import me.ling.horizons.server.LingServerInstance;
import me.ling.horizons.server.network.payload.LingLodRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handles incoming LOD requests from clients on the dedicated server.
 */
public class ServerPayloadHandler {

    public static void handleLodRequest(LingLodRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                var instance = LingCommon.getInstance();
                if (instance instanceof LingServerInstance serverInstance) {
                    serverInstance.getStreamingService().handleRequest(player, payload);
                }
            }
        });
    }
}
