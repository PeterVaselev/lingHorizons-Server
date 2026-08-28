package me.ling.horizons.common.util.cpu;

// Represents the layout of the current cpu running on
public class CpuLayout {
    private CpuLayout(){}

    public static void setThreadAffinity(Core... cores) {
    }

    public static void setThreadAffinity(Affinity... affinities) {
    }

    public record Affinity(long msk, short group) {}
    public record Core(boolean isEfficiency, Affinity affinity) {}

    public static final Core[] CORES = new Core[0];
}
