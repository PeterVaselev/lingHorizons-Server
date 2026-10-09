package me.ling.horizons.server.integration;

import me.ling.horizons.common.Logger;
import me.ling.horizons.server.config.LingServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
     * Checks whether Chunky has any uncompleted / paused saved tasks on disk.
     */
    public static boolean hasSavedChunkyTask() {
        try {
            File tasksDir = new File("config/chunky/tasks");
            if (!tasksDir.exists() || !tasksDir.isDirectory()) {
                return false;
            }
            return checkDirectoryForTasks(tasksDir);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkDirectoryForTasks(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory()) {
                if (checkDirectoryForTasks(f)) return true;
            } else if (f.getName().endsWith(".properties")) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line;
                    boolean cancelled = false;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("cancelled=true")) {
                            cancelled = true;
                            break;
                        }
                    }
                    if (!cancelled) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
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
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> resumeChunky(server));
            }
        }
    }

    /**
     * Executes the configured Chunky startup sequence after the server has booted (ServerStartedEvent).
     */
    public static void onServerStarted(MinecraftServer server) {
        if (!isChunkyLoaded() || server == null) {
            return;
        }

        if (!LingServerConfig.autoStartChunkyOnBoot) {
            return;
        }

        int delay = Math.max(1, LingServerConfig.autoStartDelaySeconds);
        if (!LingServerConfig.silentChunkyBridgeLogs) {
            Logger.info("[Chunky Auto-Bridge] ServerStartedEvent detected: Waiting " + delay + " seconds before starting Chunky pregeneration...");
        }

        Thread startupThread = new Thread(() -> {
            try {
                Thread.sleep(delay * 1000L);

                if (!server.isRunning() || server.getPlayerCount() > 0) {
                    if (!LingServerConfig.silentChunkyBridgeLogs && server.getPlayerCount() > 0) {
                        Logger.info("[Chunky Auto-Bridge] Players already online after " + delay + "s delay -> Skipping Chunky startup sequence.");
                    }
                    return;
                }

                server.execute(() -> {
                    try {
                        if (!server.isRunning() || server.getPlayerCount() > 0) return;
                        CommandSourceStack source = server.createCommandSourceStack();

                        // 🔍 Smart Auto-Detection: Resume existing uncompleted Chunky tasks without prompting
                        if (LingServerConfig.autoDetectExistingTask && hasSavedChunkyTask()) {
                            if (!LingServerConfig.silentChunkyBridgeLogs) {
                                Logger.info("[Chunky Auto-Bridge] Existing saved Chunky task detected -> Automatically executing '/chunky continue' to resume progress safely!");
                            }
                            server.getCommands().performPrefixedCommand(source, "chunky continue");
                        } else {
                            if (!LingServerConfig.silentChunkyBridgeLogs) {
                                Logger.info("[Chunky Auto-Bridge] No existing Chunky task found -> Executing new pregeneration command sequence...");
                            }

                            for (String cmd : LingServerConfig.autoStartCommands) {
                                if (cmd != null && !cmd.trim().isEmpty()) {
                                    String cleanCmd = cmd.trim().startsWith("/") ? cmd.trim().substring(1) : cmd.trim();
                                    if (!LingServerConfig.silentChunkyBridgeLogs) {
                                        Logger.info("[Chunky Auto-Bridge] Running command: /" + cleanCmd);
                                    }
                                    server.getCommands().performPrefixedCommand(source, cleanCmd);
                                }
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
                if (server.getPlayerCount() == 0) return;
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
     * Dispatches "chunky continue" on the server thread.
     */
    public static void resumeChunky(MinecraftServer server) {
        if (server == null) return;
        server.execute(() -> {
            try {
                if (!server.isRunning() || server.getPlayerCount() > 0) return;
                if (!LingServerConfig.silentChunkyBridgeLogs) {
                    Logger.info("[Chunky Auto-Bridge] Server is idle (0 players) -> Resuming Chunky background generation & LOD processing");
                }
                CommandSourceStack source = server.createCommandSourceStack();
                server.getCommands().performPrefixedCommand(source, "chunky continue");
            } catch (Exception e) {
                Logger.error("Failed to resume Chunky via Auto-Bridge", e);
            }
        });
    }

    /**
     * Called during ServerStoppingEvent to ensure Chunky immediately halts generation before server saves worlds.
     */
    public static void onServerStopping(MinecraftServer server) {
        if (!isChunkyLoaded() || server == null) return;
        try {
            Logger.info("[Chunky Auto-Bridge] Halting Chunky generation before server saves worlds...");
            CommandSourceStack source = server.createCommandSourceStack();
            server.getCommands().performPrefixedCommand(source, "chunky pause");
        } catch (Exception e) {
            Logger.error("Failed to pause Chunky during server stopping", e);
        }
    }
}
