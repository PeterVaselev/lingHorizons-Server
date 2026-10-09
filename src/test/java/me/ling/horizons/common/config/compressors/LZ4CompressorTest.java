package me.ling.horizons.common.config.compressors;

import me.ling.horizons.common.util.MemoryBuffer;
import me.ling.horizons.common.util.UnsafeUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LZ4CompressorTest {
    @Test
    void decompressesIntoCompleteSectionBuffer() {
        var compressor = new LZ4Compressor();
        var input = new MemoryBuffer(65536);
        try {
            input.zero();
            var encoded = compressor.compress(input);
            try {
                var decoded = compressor.decompress(encoded);
                assertEquals(input.size, decoded.size);
                assertEquals(0, decoded.asByteBuffer().compareTo(input.asByteBuffer()));
            } finally {
                encoded.free();
            }
        } finally {
            input.free();
        }
    }

    @Test
    void rejectsInvalidUncompressedSize() {
        var compressor = new LZ4Compressor();
        var encoded = new MemoryBuffer(4);
        try {
            UnsafeUtil.putInt(encoded.address, -1);
            assertThrows(IllegalArgumentException.class, () -> compressor.decompress(encoded));
        } finally {
            encoded.free();
        }
    }

    @Test
    void rejectsTruncatedCompressedPayload() {
        var compressor = new LZ4Compressor();
        var encoded = new MemoryBuffer(5);
        try {
            UnsafeUtil.putInt(encoded.address, 65536);
            UnsafeUtil.putByte(encoded.address + 4, (byte) 0);
            assertThrows(RuntimeException.class, () -> compressor.decompress(encoded));
        } finally {
            encoded.free();
        }
    }
}
