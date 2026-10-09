package me.ling.horizons.server.integration;

import me.ling.horizons.common.Logger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.ChunkyProvider;
import org.popcraft.chunky.GenerationTask;
import org.popcraft.chunky.api.ChunkyAPI;
import org.popcraft.chunky.event.task.GenerationTaskFinishEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Runs at most one configured Chunky task while the server is empty. */
final class ChunkyPregenQueue {
    private final MinecraftServer server;
    private final Chunky chunky;
    private final ChunkyAPI api;
    private final List<String> dimensions;
    private final int radiusBlocks;
    private final ChunkyPregenProgress progress;
    private final Consumer<GenerationTaskFinishEvent> finishListener = this::onTaskFinished;

    private volatile boolean closed;
    private boolean halted;
    private boolean pausedForLoad;
    private boolean resumePending;
    private int slowTickChecks;
    private String activeDimension;

    ChunkyPregenQueue(MinecraftServer server, List<String> dimensions, int radiusBlocks) throws IOException {
        this.server = server;
        this.chunky = ChunkyProvider.get();
        if (this.chunky == null) throw new IllegalStateException("Chunky is not initialized");
        this.api = this.chunky.getApi();
        this.dimensions = new ArrayList<>(new LinkedHashSet<>(dimensions.stream()
                .filter(dimension -> dimension != null && !dimension.isBlank())
                .map(String::trim)
                .toList()));
        this.radiusBlocks = radiusBlocks;
        this.progress = new ChunkyPregenProgress(
                server.getWorldPath(LevelResource.ROOT).resolve("ling_horizons/chunky_pregen.properties"),
                this.dimensions, radiusBlocks);
        this.chunky.getEventBus().subscribe(GenerationTaskFinishEvent.class, this.finishListener);
    }

    void startAfterDelay(int seconds) {
        scheduleWatchdog();
        CompletableFuture.delayedExecutor(seconds, TimeUnit.SECONDS).execute(() ->
                this.server.execute(this::pump));
    }

    void onPlayerCountChanged(int reportedCount) {
        this.server.execute(() -> {
            if (this.closed) return;
            if (reportedCount > 0) {
                this.resumePending = true;
                pauseAllRunningTasks();
            } else {
                // Logout events can arrive just before the player list is updated.
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() ->
                        this.server.execute(this::pump));
            }
        });
    }

    void close() {
        this.closed = true;
        this.chunky.getEventBus().unsubscribe(GenerationTaskFinishEvent.class, this.finishListener);
    }

    private void onTaskFinished(GenerationTaskFinishEvent event) {
        // Chunky calls this on its worker, after removing the task from its map.
        this.server.execute(() -> {
            if (this.closed) return;
            var task = event.generationTask();
            String dimension = task.getSelection().world().getName();
            if (dimension.equals(this.activeDimension)) {
                if (task.getProgress().isComplete()) {
                    completeDimension(dimension);
                } else if (task.isCancelled() && !task.getChunkIterator().hasNext()) {
                    // Chunky emits its finish event before the last asynchronous chunk
                    // callbacks necessarily update Progress.complete.
                    waitForCompletion(task, dimension, 0);
                } else if (!this.resumePending) {
                    // A manual pause, cancellation, or failed task should not silently
                    // start another dimension.
                    Logger.warn("[Chunky Auto-Bridge] Task stopped before completion in " + dimension + "; queue is waiting");
                    this.halted = true;
                }
            }
            this.pump();
        });
    }

    private void waitForCompletion(GenerationTask task, String dimension, int attempt) {
        if (this.closed) return;
        if (task.getProgress().isComplete()) {
            completeDimension(dimension);
            pump();
        } else if (attempt < 60) {
            CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() ->
                    this.server.execute(() -> waitForCompletion(task, dimension, attempt + 1)));
        } else {
            Logger.error("[Chunky Auto-Bridge] Chunk callbacks did not finish for " + dimension
                    + " within two minutes; queue stopped");
            this.halted = true;
        }
    }

    private void completeDimension(String dimension) {
        try {
            this.progress.markComplete(dimension);
            Logger.info("[Chunky Auto-Bridge] Completed " + dimension + "; advancing to the next dimension");
        } catch (IOException e) {
            Logger.error("Could not save Chunky pregeneration progress; stopping queue", e);
            this.halted = true;
        }
        this.activeDimension = null;
        this.resumePending = false;
    }

    private void pauseAllRunningTasks() {
        for (String dimension : List.copyOf(this.chunky.getGenerationTasks().keySet())) {
            this.api.pauseTask(dimension);
        }
    }

    private void scheduleWatchdog() {
        long scheduledAt = System.nanoTime();
        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() ->
                this.server.execute(() -> {
                    if (this.closed || !this.server.isRunning()) return;
                    long maxHeap = Runtime.getRuntime().maxMemory();
                    long usedHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    double heapFraction = (double) usedHeap / maxHeap;
                    long queueLagMillis = Math.max(0, TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - scheduledAt) - 10_000);
                    this.slowTickChecks = queueLagMillis > 2000 ? this.slowTickChecks + 1 : 0;
                    if (!this.pausedForLoad && (heapFraction > 0.80 || this.slowTickChecks >= 3)) {
                        this.pausedForLoad = true;
                        this.resumePending = true;
                        this.pauseAllRunningTasks();
                        Logger.warn("[Chunky Auto-Bridge] Pausing pregeneration for server load: heap "
                                + Math.round(heapFraction * 100) + "%, main-thread queue lag " + queueLagMillis + " ms");
                    } else if (this.pausedForLoad && heapFraction < 0.70 && queueLagMillis < 500) {
                        this.pausedForLoad = false;
                        this.slowTickChecks = 0;
                        this.pump();
                    }
                    scheduleWatchdog();
                }));
    }

    private void pump() {
        if (this.closed || this.halted || !this.server.isRunning()
                || this.server.getPlayerCount() > 0 || this.pausedForLoad) return;

        if (this.chunky.getGenerationTasks().size() > 1) {
            // Chunky may have auto-resumed old tasks before this integration started.
            this.resumePending = true;
            this.pauseAllRunningTasks();
            return;
        }
        if (!this.chunky.getGenerationTasks().isEmpty()) return;

        if (this.activeDimension != null) {
            if (this.resumePending && this.api.continueTask(this.activeDimension)) {
                this.resumePending = false;
                Logger.info("[Chunky Auto-Bridge] Continued " + this.activeDimension + " after players left");
            }
            return;
        }

        for (String dimension : this.dimensions) {
            if (this.progress.isComplete(dimension)) continue;
            var world = this.chunky.getServer().getWorld(dimension);
            if (world.isEmpty()) {
                Logger.error("[Chunky Auto-Bridge] Unknown dimension " + dimension + "; stopping pregeneration queue");
                this.halted = true;
                return;
            }

            var saved = this.chunky.getTaskLoader().loadTask(world.get());
            if (saved.isPresent() && !saved.get().isCancelled()) {
                var selection = saved.get().getSelection();
                if (!"circle".equals(selection.shape()) || selection.centerX() != 0 || selection.centerZ() != 0
                        || selection.radiusX() != this.radiusBlocks || selection.radiusZ() != this.radiusBlocks) {
                    Logger.error("[Chunky Auto-Bridge] Existing task for " + dimension
                            + " has a different selection; stopping queue to preserve it");
                    this.halted = true;
                    return;
                }
                if (this.api.continueTask(dimension)) {
                    this.activeDimension = dimension;
                    this.resumePending = false;
                    Logger.info("[Chunky Auto-Bridge] Continued saved task for " + dimension);
                    return;
                }
                Logger.error("[Chunky Auto-Bridge] Could not continue saved task for " + dimension);
                this.halted = true;
                return;
            }

            if (this.api.startTask(dimension, "circle", 0, 0, this.radiusBlocks, this.radiusBlocks, "region")) {
                this.activeDimension = dimension;
                this.resumePending = false;
                Logger.info("[Chunky Auto-Bridge] Started " + dimension + " circle radius " + this.radiusBlocks + " blocks");
            } else {
                Logger.error("[Chunky Auto-Bridge] Could not start task for " + dimension + "; stopping queue");
                this.halted = true;
            }
            return;
        }

        Logger.info("[Chunky Auto-Bridge] Pregeneration complete in all configured dimensions");
    }
}
