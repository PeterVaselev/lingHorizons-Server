package me.ling.horizons.common.config.storage.inmemory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import me.ling.horizons.common.config.ConfigBuildCtx;
import me.ling.horizons.common.config.storage.StorageBackend;
import me.ling.horizons.common.config.storage.StorageConfig;
import me.ling.horizons.common.util.MemoryBuffer;
import net.minecraft.world.level.levelgen.RandomSupport;
import org.apache.commons.lang3.stream.Streams;

import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

//TODO: replace synchronize with StampedLock
public class MemoryStorageBackend extends StorageBackend {
    private final Long2ObjectMap<MemoryBuffer>[] maps;
    private final Int2ObjectMap<ByteBuffer> idMappings = new Int2ObjectOpenHashMap<>();

    public MemoryStorageBackend() {
        this(4);
    }

    @SuppressWarnings("unchecked")
    public MemoryStorageBackend(int locks) {
        this.maps = new Long2ObjectMap[locks];
        for (int i = 0; i < locks; i++) {
            this.maps[i] = new Long2ObjectOpenHashMap<>();
        }
    }

    private Long2ObjectMap<MemoryBuffer> getMap(long key) {
        return this.maps[(int) Math.abs(RandomSupport.mixStafford13(key)%this.maps.length)];
    }

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        var map = this.getMap(key);
        synchronized (map) {
            var data = map.get(key);
            if (data != null) {
                data.cpyTo(scratch.address);
                return scratch.subSize(data.size);
            } else {
                return null;
            }
        }
    }

    @Override
    public void iterateStoredSectionPositions(LongConsumer consumer) {
        for (var map : this.maps) {
            synchronized (map) {
                map.keySet().forEach(consumer);
            }
        }
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        var map = this.getMap(key);
        synchronized (map) {
            var cpy = data.copy();
            var old = map.put(key, cpy);
            if (old != null) {
                old.free();
            }
        }
    }

    @Override
    public void deleteSectionData(long key) {
        var map = this.getMap(key);
        synchronized (map) {
            var data = map.remove(key);
            if (data != null) {
                data.free();
            }
        }
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        synchronized (this.idMappings) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            data.rewind();
            this.idMappings.put(id, ByteBuffer.wrap(bytes));
        }
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        Int2ObjectOpenHashMap<byte[]> out = new Int2ObjectOpenHashMap<>();
        synchronized (this.idMappings) {
            for (var entry : this.idMappings.int2ObjectEntrySet()) {
                var buf = new byte[entry.getValue().remaining()];
                entry.getValue().get(buf);
                entry.getValue().rewind();
                out.put(entry.getIntKey(), buf);
            }
        }
        return out;
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {
        Streams.of(this.maps).map(Long2ObjectMap::values).flatMap(ObjectCollection::stream).forEach(MemoryBuffer::free);
        this.idMappings.clear();
    }

    public static class Config extends StorageConfig {
        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new MemoryStorageBackend();
        }

        public static String getConfigTypeName() {
            return "Memory";
        }
    }
}
