package me.ling.horizons.common.config.compressors;

import me.ling.horizons.common.config.ConfigBuildCtx;
import me.ling.horizons.common.util.MemoryBuffer;
import me.ling.horizons.common.util.ThreadLocalMemoryBuffer;
import me.ling.horizons.common.util.UnsafeUtil;
import me.ling.horizons.common.world.SaveLoadSystem;
import net.jpountz.lz4.LZ4Factory;

public class LZ4Compressor implements StorageCompressor {
    private static final ThreadLocalMemoryBuffer SCRATCH = new ThreadLocalMemoryBuffer(SaveLoadSystem.BIGGEST_SERIALIZED_SECTION_SIZE + 1024);

    private final net.jpountz.lz4.LZ4Compressor compressor;
    private final net.jpountz.lz4.LZ4SafeDecompressor decompressor;
    public LZ4Compressor() {
        this.decompressor = LZ4Factory.fastestInstance().safeDecompressor();
        this.compressor = LZ4Factory.fastestInstance().fastCompressor();
    }

    @Override
    public MemoryBuffer compress(MemoryBuffer saveData) {
        var res = new MemoryBuffer(this.compressor.maxCompressedLength((int) saveData.size)+4);
        UnsafeUtil.putInt(res.address, (int) saveData.size);
        int size = this.compressor.compress(saveData.asByteBuffer(), 0, (int) saveData.size, res.asByteBuffer(), 4, (int) res.size-4);
        return res.subSize(size+4);
    }

    @Override
    public MemoryBuffer decompress(MemoryBuffer saveData) {
        var res = SCRATCH.get().createUntrackedUnfreeableReference();
        if (saveData.size < Integer.BYTES) {
            throw new IllegalArgumentException("Truncated LZ4 section header");
        }
        int expectedSize = UnsafeUtil.getInt(saveData.address);
        if (expectedSize <= 0 || expectedSize > res.size) {
            throw new IllegalArgumentException("Invalid LZ4 section size: " + expectedSize);
        }
        int actualSize = this.decompressor.decompress(saveData.asByteBuffer(), 4, (int) saveData.size - 4,
                res.asByteBuffer(), 0, expectedSize);
        if (actualSize != expectedSize) {
            throw new IllegalArgumentException("LZ4 section size mismatch: " + actualSize + " != " + expectedSize);
        }
        return res.subSize(actualSize);
    }

    @Override
    public void close() {
    }

    public static class Config extends CompressorConfig {

        @Override
        public StorageCompressor build(ConfigBuildCtx ctx) {
            return new LZ4Compressor();
        }

        public static String getConfigTypeName() {
            return "LZ4";
        }
    }
}
