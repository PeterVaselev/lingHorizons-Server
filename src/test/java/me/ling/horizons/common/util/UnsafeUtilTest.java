package me.ling.horizons.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnsafeUtilTest {
    @Test
    void byteBufferWritesIntoNativeMemory() {
        var memory = new MemoryBuffer(8);
        try {
            memory.asByteBuffer().put(0, (byte) 42);
            assertEquals(42, UnsafeUtil.getByte(memory.address));
            UnsafeUtil.putByte(memory.address + 1, (byte) 7);
            assertEquals(7, memory.asByteBuffer().get(1));
        } finally {
            memory.free();
        }
    }
}
