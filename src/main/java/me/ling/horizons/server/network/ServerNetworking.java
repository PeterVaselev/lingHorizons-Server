package me.ling.horizons.server.network;

import me.ling.horizons.server.network.payload.LingHelloPayload;
import me.ling.horizons.server.network.payload.LingLodDataPayload;
import me.ling.horizons.server.network.payload.LingLodRequestPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers network payloads for the dedicated server.
 */
public class ServerNetworking {
    public static final String PROTOCOL_VERSION = "1";

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToClient(
                LingHelloPayload.TYPE,
                LingHelloPayload.STREAM_CODEC,
                (payload, context) -> {}
        );

        registrar.playToServer(
                LingLodRequestPayload.TYPE,
                LingLodRequestPayload.STREAM_CODEC,
                ServerPayloadHandler::handleLodRequest
        );

        registrar.playToClient(
                LingLodDataPayload.TYPE,
                LingLodDataPayload.STREAM_CODEC,
                (payload, context) -> {}
        );
    }
}
