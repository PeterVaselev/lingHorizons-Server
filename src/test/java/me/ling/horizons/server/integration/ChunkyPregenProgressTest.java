package me.ling.horizons.server.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkyPregenProgressTest {
    @TempDir Path directory;

    @Test
    void completionSurvivesRestartAndPlanChangeStartsFresh() throws Exception {
        Path file = directory.resolve("world/ling_horizons/chunky_pregen.properties");
        List<String> dimensions = List.of("minecraft:overworld", "aether:the_aether");
        var firstRun = new ChunkyPregenProgress(file, dimensions, 3000);
        firstRun.markComplete("minecraft:overworld");

        var restarted = new ChunkyPregenProgress(file, dimensions, 3000);
        assertTrue(restarted.isComplete("minecraft:overworld"));
        assertFalse(restarted.isComplete("aether:the_aether"));
        restarted.markComplete("aether:the_aether");
        assertTrue(new ChunkyPregenProgress(file, dimensions, 3000).isComplete("aether:the_aether"));

        var changedRadius = new ChunkyPregenProgress(file, dimensions, 4000);
        assertFalse(changedRadius.isComplete("minecraft:overworld"));
        assertFalse(changedRadius.isComplete("aether:the_aether"));
    }
}
