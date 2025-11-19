package redxax.oxy.remotely.ui.widgets.worldmap;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MapTileCache {
    private final ChunkLoader chunkLoader;
    private final AssetManager assetManager;
    private final Map<Long, BufferedImage> overviewCache = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> pending = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;

    public MapTileCache(ChunkLoader chunkLoader, AssetManager assetManager) {
        this.chunkLoader = chunkLoader;
        this.assetManager = assetManager;
        this.executor = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "TileGen");
            t.setDaemon(true);
            return t;
        });
    }

    public BufferedImage getOverviewTile(int rx, int rz, int centerRx, int centerRz) {
        long key = getKey(rx, rz);
        if (overviewCache.containsKey(key)) return overviewCache.get(key);

        if (!pending.containsKey(key)) {
            pending.put(key, true);
            int distSq = (rx - centerRx) * (rx - centerRx) + (rz - centerRz) * (rz - centerRz);
            executor.execute(new TileTask(rx, rz, key, distSq));
        }
        return null;
    }

    private class TileTask implements Runnable, Comparable<TileTask> {
        private final int rx, rz;
        private final long key;
        private final int priority;

        public TileTask(int rx, int rz, long key, int priority) {
            this.rx = rx;
            this.rz = rz;
            this.key = key;
            this.priority = priority;
        }

        @Override
        public void run() {
            try {
                generate(rx, rz, key);
            } finally {
                pending.remove(key);
            }
        }

        @Override
        public int compareTo(TileTask o) {
            return Integer.compare(this.priority, o.priority);
        }
    }

    private void generate(int rx, int rz, long key) {
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[512 * 512];
        boolean empty = true;

        for (int cz = 0; cz < 32; cz++) {
            for (int cx = 0; cx < 32; cx++) {
                ChunkData chunk = chunkLoader.getChunk(rx * 32 + cx, rz * 32 + cz);
                if (chunk == null) continue;
                empty = false;

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        String id = chunk.getBlockId(x, z);
                        if (id == null) continue;

                        int px = cx * 16 + x;
                        int pz = cz * 16 + z;

                        int h = chunk.getHeight(x, z);
                        int hNorth = (z > 0) ? chunk.getHeight(x, z - 1) : h;

                        float shade = (h < hNorth) ? 0.75f : (h > hNorth) ? 1.15f : 1.0f;

                        String biome = chunk.getBiomeId(x, z);
                        int baseColor = assetManager.getBlockColor(id, biome);

                        pixels[pz * 512 + px] = applyShade(baseColor, shade);
                    }
                }
            }
        }

        if (!empty) {
            img.setRGB(0, 0, 512, 512, pixels, 0, 512);
            overviewCache.put(key, img);
        } else {
            overviewCache.put(key, new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        }
    }

    private int applyShade(int color, float mult) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * mult));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * mult));
        int b = Math.min(255, (int) ((color & 0xFF) * mult));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private long getKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }

    public void clear() {
        overviewCache.clear();
        pending.clear();
        executor.getQueue().clear();
    }

    public void shutdown() {
        executor.shutdownNow();
        overviewCache.clear();
    }
}
