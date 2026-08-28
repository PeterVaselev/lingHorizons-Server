package me.ling.horizons.common.config.compressors;

import me.ling.horizons.common.config.ConfigBuildCtx;
import me.ling.horizons.common.util.MemoryBuffer;
import net.jpountz.lz4.LZ4Factory;

public class ZSTDCompressor implements StorageCompressor {
    private final LZ4Compressor delegate;

    public ZSTDCompressor(int level) {
        this.delegate = new LZ4Compressor();
    }

    @Override
    public MemoryBuffer compress(MemoryBuffer saveData) {
        return this.delegate.compress(saveData);
    }

    @Override
    public MemoryBuffer decompress(MemoryBuffer saveData) {
        return this.delegate.decompress(saveData);
    }

    @Override
    public void close() {
        this.delegate.close();
    }

    public static class Config extends CompressorConfig {
        public int compressionLevel;

        @Override
        public StorageCompressor build(ConfigBuildCtx ctx) {
            return new ZSTDCompressor(this.compressionLevel);
        }

        public static String getConfigTypeName() {
            return "ZSTD";
        }
    }
}
