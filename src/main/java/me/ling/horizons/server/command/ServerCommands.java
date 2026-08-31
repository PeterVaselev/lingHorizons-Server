package me.ling.horizons.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.ling.horizons.commonImpl.LingCommon;
import me.ling.horizons.commonImpl.LingInstance;
import me.ling.horizons.server.LingServerInstance;
import me.ling.horizons.server.config.LingServerConfig;
import me.ling.horizons.server.importer.ServerWorldImporter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "ling_horizons_server")
public class ServerCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("ling")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            var instance = LingCommon.getInstance();
                            src.sendSuccess(() -> Component.literal("=== LING Horizons Master Server Status ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                            src.sendSuccess(() -> Component.literal("• Streaming Enabled: " + (LingServerConfig.streamingEnabled ? "YES" : "NO")).withStyle(ChatFormatting.AQUA), false);
                            src.sendSuccess(() -> Component.literal("• Max Streaming Radius: " + LingServerConfig.maxStreamingRadiusChunks + " chunks (" + (LingServerConfig.maxStreamingRadiusChunks * 16) + " blocks)").withStyle(ChatFormatting.AQUA), false);
                            src.sendSuccess(() -> Component.literal("• Bandwidth Limit: " + LingServerConfig.maxBandwidthPerPlayerKB + " KB/s per player").withStyle(ChatFormatting.AQUA), false);
                            src.sendSuccess(() -> Component.literal("• Chunky Auto-Bridge: " + (LingServerConfig.autoManageChunky ? "ACTIVE" : "DISABLED")).withStyle(ChatFormatting.AQUA), false);
                            src.sendSuccess(() -> Component.literal("• Region Importer Busy: " + (ServerWorldImporter.isImporting() ? "YES (Importing...)" : "IDLE")).withStyle(ChatFormatting.AQUA), false);
                            return 1;
                        })
                )
                .then(Commands.literal("import")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            if (ServerWorldImporter.isImporting()) {
                                src.sendFailure(Component.literal("An import task is already running!"));
                                return 0;
                            }
                            src.sendSuccess(() -> Component.literal("Starting Master MCA Region Import for ALL dimensions in background...").withStyle(ChatFormatting.GREEN), true);
                            ServerWorldImporter.startImportAsync(src.getServer(), "all").thenRun(() -> {
                                src.sendSuccess(() -> Component.literal("MCA Region Import complete! Master RocksDB is up to date.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                            });
                            return 1;
                        })
                        .then(Commands.argument("dimension", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    String dim = StringArgumentType.getString(ctx, "dimension");
                                    if (ServerWorldImporter.isImporting()) {
                                        src.sendFailure(Component.literal("An import task is already running!"));
                                        return 0;
                                    }
                                    src.sendSuccess(() -> Component.literal("Starting Master MCA Region Import for dimension: " + dim).withStyle(ChatFormatting.GREEN), true);
                                    ServerWorldImporter.startImportAsync(src.getServer(), dim).thenRun(() -> {
                                        src.sendSuccess(() -> Component.literal("MCA Region Import for " + dim + " complete!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                                    });
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("cancel")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            if (!ServerWorldImporter.isImporting()) {
                                src.sendFailure(Component.literal("No import task is currently running."));
                                return 0;
                            }
                            ServerWorldImporter.cancelImport();
                            src.sendSuccess(() -> Component.literal("Import task cancel signal sent.").withStyle(ChatFormatting.YELLOW), true);
                            return 1;
                        })
                )
        );
    }
}
