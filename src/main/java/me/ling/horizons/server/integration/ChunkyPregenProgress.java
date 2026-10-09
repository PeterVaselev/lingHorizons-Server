package me.ling.horizons.server.integration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Persists completed dimensions for one immutable pregeneration plan. */
final class ChunkyPregenProgress {
    private final Path path;
    private final String plan;
    private final Set<String> completed = new LinkedHashSet<>();

    ChunkyPregenProgress(Path path, List<String> dimensions, int radiusBlocks) throws IOException {
        this.path = path;
        this.plan = radiusBlocks + "|" + String.join(",", dimensions);
        if (Files.exists(path)) {
            var properties = new Properties();
            try (var input = Files.newInputStream(path)) {
                properties.load(input);
            }
            if (this.plan.equals(properties.getProperty("plan"))) {
                for (String dimension : dimensions) {
                    if (Boolean.parseBoolean(properties.getProperty("completed." + dimension, "false"))) {
                        this.completed.add(dimension);
                    }
                }
            }
        }
    }

    boolean isComplete(String dimension) {
        return this.completed.contains(dimension);
    }

    void markComplete(String dimension) throws IOException {
        if (this.completed.contains(dimension)) return;
        var properties = new Properties();
        properties.setProperty("plan", this.plan);
        for (String completedDimension : this.completed) {
            properties.setProperty("completed." + completedDimension, "true");
        }
        properties.setProperty("completed." + dimension, "true");
        Files.createDirectories(this.path.getParent());
        Path temporary = this.path.resolveSibling(this.path.getFileName() + ".tmp");
        try (var output = Files.newOutputStream(temporary)) {
            properties.store(output, "LING Horizons Chunky pregeneration progress");
        }
        try {
            Files.move(temporary, this.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING);
        }
        this.completed.add(dimension);
    }
}
