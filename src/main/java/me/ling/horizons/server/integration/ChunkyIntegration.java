package me.ling.horizons.server.integration;

import me.ling.horizons.common.Logger;
import me.ling.horizons.server.config.LingServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/**
 * Built-in Chunky Auto-Bridge for LING Horizons Server.
 * Automatically pauses Chunky chunk generation during active gameplay (>= 1 player)
 * and resumes Chunky generation during offline downtime (0 players).
 */
public class ChunkyIntegration {

    private static Boolean chunkyLoaded = null;
    private static volatile boolean isPausedByBridge = false;

    public static boolean isChunkyLoaded() {
        if (chunkyLoaded == null) {
            chunkyLoaded = ModList.get().isLoaded("chunky");
        }
        return chunkyLoaded;
    }

    /**
     * Called whenever player count changes on the server.
     */
    public static void onPlayerCountChanged(MinecraftServer server, int newPlayerCount) {
        if (!LingServerConfig.autoManageChunky || !isChunkyLoaded() || server == null) {
            return;
        }

        if (newPlayerCount > 0) {
            // Players are online -> Pause Chunky to ensure 100% smooth TPS
            if (!isPausedByBridge) {
                isPausedByBridge = true;
                pauseChunky(server);
            }
        } else {
            // 0 players online -> Resume Chunky to pregenerate chunks & build LODs in background
            if (isPausedByBridge) {
                isPausedByBridge = false;
                resumeChunky(server);
            }
        }
    }

    /**
     * Executes the configured Chunky startup sequence after the server has booted.
     */
    public static void onServerStarted(MinecraftServer server) {
        if (!isChunkyLoaded() || server == null) {
            return;
        }

        if (!LingServerConfig.autoStartChunkyOnBoot) {
            return;
        }

        int delay = Math.max(1, LingServerConfig.autoStartDelaySeconds);
        Thread startupThread = new Thread(() -> {
            try {
                Thread.sleep(delay * 1000L);

                if (!server.isRunning() || server.getPlayerCount() > 0) {
                    return;
                }

                server.execute(() -> {
                    try {
                        if (!LingServerConfig.silentChunkyBridgeLogs) {
                            Logger.info("[Chunky Auto-Bridge] Executing server startup Chunky pregeneration sequence...");
                        }

                        CommandSourceStack source = server.createCommandSourceStack();
                        for (String cmd : LingServerConfig.autoStartCommands) {
                            if (cmd != null && !cmd.trim().isEmpty()) {
                                String cleanCmd = cmd.trim().startsWith("/") ? cmd.trim().substring(1) : cmd.trim();
                                if (!LingServerConfig.silentChunkyBridgeLogs) {
                                    Logger.info("[Chunky Auto-Bridge] Running command: /" + cleanCmd);
                                }
                                server.getCommands().performPrefixedCommand(source, cleanCmd);
                            }
                        }
                    } catch (Exception e) {
                        Logger.error("Failed to execute startup Chunky commands", e);
                    }
                });
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                Logger.error("Error in Chunky startup sequence timer", e);
            }
        }, "Chunky Auto-Start Worker");

        startupThread.setDaemon(true);
        startupThread.start();
    }

    /**
     * Dispatches "chunky pause" on the server thread.
     */
    public static void pauseChunky(MinecraftServer server) {
        if (server == null) return;
        server.execute(() -> {
            try {
                if (!LingServerConfig.silentChunkyBridgeLogs) {
                    Logger.info("[Chunky Auto-Bridge] Players detected online -> Pausing Chunky generation for maximum gameplay TPS");
                }
                CommandSourceStack source = server.createCommandSourceStack();
                server.getCommands().performPrefixedCommand(source, "chunky pause");
            } catch (Exception e) {
                Logger.error("Failed to pause Chunky via Auto-Bridge", e);
            }
        });
    }

    /**
     * Dispatches "chunky resume" on the server thread.
     */
    public static void resumeChunky(MinecraftServer server) {
        if (server == null) return;
        server.execute(() -> {
            try {
                if (!LingServerConfig.silentChunkyBridgeLogs) {
                    Logger.info("[Chunky Auto-Bridge] Server is idle (0 players) -> Resuming Chunky background generation & LOD processing");
                }
                CommandSourceStack source = server.createCommandSourceStack();
                server.getCommands().performPrefixedCommand(source, "chunky resume");
            } catch (Exception e) {
                Logger.error("Failed to resume Chunky via Auto-Bridge", e);
            }
        });
    }
}
