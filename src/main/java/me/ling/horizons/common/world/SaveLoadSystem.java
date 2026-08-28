package me.ling.horizons.common.world;

import me.ling.horizons.common.Logger;
import me.ling.horizons.common.util.MemoryBuffer;
import me.ling.horizons.common.util.ThreadLocalMemoryBuffer;
import me.ling.horizons.common.util.UnsafeUtil;
import me.ling.horizons.common.world.other.Mapper;

public class SaveLoadSystem {
    public static final boolean VERIFY_MEMORY_ACCESS = false;
    public static final boolean VERIFY_HASH_ON_LOAD = false;
    public static final int STORAGE_VERSION = 0;
    public static final int BIGGEST_SERIALIZED_SECTION_SIZE = 8+8+4+32768*8+WorldSection.SECTION_VOLUME*2+8;

    private record SerializationCache(short[] lutIndicesCache, long[] lutValuesCache, short[] compressedCache, long[] lutCache) {
        public SerializationCache() {
            this(new short[WorldSection.SECTION_VOLUME], new long[WorldSection.SECTION_VOLUME], new short[WorldSection.SECTION_VOLUME], new long[WorldSection.SECTION_VOLUME]);
        }
    }
    public static int lin2z(int i) {//y,z,x
        int x = i&0x1F;
        int y = (i>>10)&0x1F;
        int z = (i>>5)&0x1F;
        return Integer.expand(x,0b1001001001001)|Integer.expand(y,0b10010010010010)|Integer.expand(z,0b100100100100100);
    }

    public static int z2lin(int i) {
        int x = Integer.compress(i, 0b1001001001001);
        int y = Integer.compress(i, 0b10010010010010);
        int z = Integer.compress(i, 0b100100100100100);
        return x|(y<<10)|(z<<5);
    }

    private static final ThreadLocal<SerializationCache> CACHE = ThreadLocal.withInitial(SerializationCache::new);
    private static final ThreadLocalMemoryBuffer SCRATCH = new ThreadLocalMemoryBuffer(BIGGEST_SERIALIZED_SECTION_SIZE);

    public static MemoryBuffer serialize(WorldSection section) {
        var cache = CACHE.get();
        var data = section.data;

        long[] lutValues = cache.lutValuesCache;
        short[] compressed = cache.compressedCache;

        int lutIndex = 0;
        long pHash = 99;
        for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
            long block = data[lin2z(i)];
            pHash *= 127817112311121L;
            pHash ^= pHash >> 31;
            pHash += 9918322711L;
            pHash ^= block;

            short index = -1;
            for (short j = 0; j < lutIndex; j++) {
                if (lutValues[j] == block) {
                    index = j;
                    break;
                }
            }
            if (index == -1) {
                index = (short) lutIndex;
                lutValues[lutIndex++] = block;
            }
            compressed[i] = index;
        }

        MemoryBuffer raw = SCRATCH.get().createUntrackedUnfreeableReference();
        long ptr = raw.address;

        long hash = section.key^(lutIndex*1293481298141L);
        UnsafeUtil.putLong(ptr, section.key); ptr += 8;

        long metadata = 0;
        metadata |= Byte.toUnsignedLong(section.nonEmptyChildren);
        UnsafeUtil.putLong(ptr, metadata); ptr += 8;

        hash ^= metadata; hash *= 1242629872171L;

        UnsafeUtil.putInt(ptr, lutIndex); ptr += 4;
        for (int i = 0; i < lutIndex; i++) {
            long id = lutValues[i];
            UnsafeUtil.putLong(ptr, id); ptr += 8;
            hash *= 1230987149811L;
            hash += 12831;
            hash ^= id;
        }
        hash ^= pHash;

        UnsafeUtil.memcpy(compressed, ptr); ptr += compressed.length*2L;

        UnsafeUtil.putLong(ptr, hash); ptr += 8;

        return raw.subSize(ptr-raw.address);
    }

    public static boolean deserialize(WorldSection section, MemoryBuffer data) {
        long ptr = data.address;
        long key = UnsafeUtil.getLong(ptr); ptr += 8; if (VERIFY_MEMORY_ACCESS && data.size<=(ptr-data.address)) throw new IllegalStateException("Memory access OOB");

        long metadata = UnsafeUtil.getLong(ptr); ptr += 8; if (VERIFY_MEMORY_ACCESS && data.size<=(ptr-data.address)) throw new IllegalStateException("Memory access OOB");
        section.nonEmptyChildren = (byte) (metadata&0xFF);

        int lutLen = UnsafeUtil.getInt(ptr); ptr += 4; if (VERIFY_MEMORY_ACCESS && data.size<=(ptr-data.address)) throw new IllegalStateException("Memory access OOB");
        if (lutLen > 32*32*32) {
            throw new IllegalStateException("lutLen impossibly large, max size should be 32768 but got size " + lutLen);
        }
        //TODO: cache this in a thread local
        long[] lut = CACHE.get().lutCache;
        long hash = 0;
        if (VERIFY_HASH_ON_LOAD) {
            hash = key ^ (lutLen * 1293481298141L);
            hash ^= metadata; hash *= 1242629872171L;
        }
        for (int i = 0; i < lutLen; i++) {
            lut [i] = UnsafeUtil.getLong(ptr); ptr += 8; if (VERIFY_MEMORY_ACCESS && data.size<=(ptr-data.address)) throw new IllegalStateException("Memory access OOB");
            if (VERIFY_HASH_ON_LOAD) {
                hash *= 1230987149811L;
                hash += 12831;
                hash ^= lut[i];
            }
        }

        if (section.key != key) {
            Logger.error("Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            return false;
        }

        int nonEmptyBlockCount = 0;
        for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
            long state = lut[Short.toUnsignedInt(UnsafeUtil.getShort(ptr))]; ptr += 2; if (VERIFY_MEMORY_ACCESS && data.size<=(ptr-data.address)) throw new IllegalStateException("Memory access OOB");
            nonEmptyBlockCount += Mapper.isAir(state)?0:1;
            section.data[z2lin(i)] = state;
        }
        section.nonEmptyBlockCount = nonEmptyBlockCount;

        if (VERIFY_HASH_ON_LOAD) {
            long pHash = 99;
            for (long block : section.data) {
                pHash *= 127817112311121L;
                pHash ^= pHash >> 31;
                pHash += 9918322711L;
                pHash ^= block;
            }
            hash ^= pHash;

            long expectedHash = UnsafeUtil.getLong(ptr); ptr += 8; if (VERIFY_MEMORY_ACCESS && data.size<(ptr-data.address)) throw new IllegalStateException("Memory access OOB");
            if (expectedHash != hash) {
                Logger.error("Hash mismatch got: " + hash + " expected: " + expectedHash + " removing region");
                return false;
            }
        }
        return true;
    }
}
