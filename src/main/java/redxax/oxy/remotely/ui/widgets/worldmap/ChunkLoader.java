package redxax.oxy.remotely.ui.widgets.worldmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class ChunkLoader {
    private final Path worldFolder;

    private final LruExpireCache<Long, ChunkData> chunkCache;

    public ChunkLoader(Path worldFolder) {
        this.worldFolder = worldFolder;
        this.chunkCache = new LruExpireCache<>(2048, TimeUnit.MINUTES.toMillis(2));
    }

    public ChunkData getChunk(int cx, int cz) {
        long key = getKey(cx, cz);
        ChunkData cached = chunkCache.getIfPresent(key);
        if (cached != null) return cached;

        ChunkData loaded = loadChunk(cx, cz);
        if (loaded != null) chunkCache.put(key, loaded);
        return loaded;
    }

    private ChunkData loadChunk(int cx, int cz) {
        int rx = cx >> 5;
        int rz = cz >> 5;
        Path regionFile = worldFolder.resolve("region").resolve("r." + rx + "." + rz + ".mca");

        if (!Files.exists(regionFile)) return null;

        try {
            return RegionParser.parseChunkData(regionFile, cx & 31, cz & 31);
        } catch (IOException e) {
            return null;
        }
    }

    private long getKey(int x, int z) {
        return (((long)x) << 32) | (z & 0xffffffffL);
    }

    public void shutdown() {
        chunkCache.invalidateAll();
        chunkCache.shutdown();
    }
}