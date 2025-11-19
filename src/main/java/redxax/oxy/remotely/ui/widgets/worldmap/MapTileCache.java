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

    private static final int TILE_SIZE = 512;
    private static final int CHUNKS_PER_TILE = 32;
    private static final int CACHE_PADDING = 1;
    private static final int CACHE_WIDTH_CHUNKS = CHUNKS_PER_TILE + (CACHE_PADDING * 2);
    private static final int CACHE_WIDTH_BLOCKS = CACHE_WIDTH_CHUNKS * 16;

    public MapTileCache(ChunkLoader chunkLoader, AssetManager assetManager) {
        this.chunkLoader = chunkLoader;
        this.assetManager = assetManager;
        this.executor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "TileGen");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.setDaemon(true);
            return t;
        });
    }

    public BufferedImage getTile(int rx, int rz, int centerRx, int centerRz) {
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
            } catch (Exception e) {
                e.printStackTrace();
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
        int arraySize = CACHE_WIDTH_BLOCKS * CACHE_WIDTH_BLOCKS;
        short[] heightMap = new short[arraySize];
        int[] colorMap = new int[arraySize];
        short[] floorHeightMap = new short[arraySize];
        int[] floorColorMap = new int[arraySize];
        boolean[] isWaterMap = new boolean[arraySize];

        boolean hasData = false;

        for (int cz = -CACHE_PADDING; cz < CHUNKS_PER_TILE + CACHE_PADDING; cz++) {
            for (int cx = -CACHE_PADDING; cx < CHUNKS_PER_TILE + CACHE_PADDING; cx++) {
                ChunkData chunk = chunkLoader.getChunk(rx * CHUNKS_PER_TILE + cx, rz * CHUNKS_PER_TILE + cz);

                if (chunk != null && cx >= 0 && cx < CHUNKS_PER_TILE && cz >= 0 && cz < CHUNKS_PER_TILE) {
                    hasData = true;
                }
                if (chunk == null) continue;

                int xBase = (cx + CACHE_PADDING) * 16;
                int zBase = (cz + CACHE_PADDING) * 16;

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int idx = (zBase + z) * CACHE_WIDTH_BLOCKS + (xBase + x);

                        String id = chunk.getBlockId(x, z);
                        if (id == null || id.equals("minecraft:air")) continue;

                        String biome = chunk.getBiomeId(x, z);
                        int color = assetManager.getBlockColor(id, biome);

                        heightMap[idx] = (short) chunk.getHeight(x, z);
                        colorMap[idx] = color;

                        int alpha = (color >> 24) & 0xFF;
                        if (alpha < 250 || id.contains("water") || id.contains("ice")) {
                            isWaterMap[idx] = true;
                            String floorId = chunk.getFloorId(x, z);
                            if (floorId != null) {
                                floorColorMap[idx] = assetManager.getBlockColor(floorId, biome);
                                floorHeightMap[idx] = (short) chunk.getFloorHeight(x, z);
                            } else {
                                floorHeightMap[idx] = (short) (heightMap[idx] - 10);
                                floorColorMap[idx] = 0xFF000000;
                            }
                        } else {
                            floorHeightMap[idx] = heightMap[idx];
                        }
                    }
                }
            }
        }

        if (!hasData) {
            overviewCache.put(key, new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
            return;
        }

        BufferedImage img = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[TILE_SIZE * TILE_SIZE];

        int pixelOffset = CACHE_PADDING * 16;

        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int mapIdx = (y + pixelOffset) * CACHE_WIDTH_BLOCKS + (x + pixelOffset);
                int waterBaseColor = colorMap[mapIdx];

                if (waterBaseColor == 0) continue;

                int h = heightMap[mapIdx];
                boolean isWater = isWaterMap[mapIdx];
                int finalColor;

                double noise = MapRenderSettings.flatSurfaceNoise ?
                    ((x * 492 + y * 1823) % 100) / 100.0 * MapRenderSettings.noiseIntensity : 0;

                if (isWater && MapRenderSettings.advancedWaterRendering) {
                    int floorH = floorHeightMap[mapIdx];
                    int floorColor = floorColorMap[mapIdx];

                    int depth = h - floorH;

                    float floorSlope = calculateSlope(floorHeightMap, mapIdx, CACHE_WIDTH_BLOCKS, floorH);
                    float floorShadow = calculateShadow(heightMap, mapIdx, CACHE_WIDTH_BLOCKS, h);
                    int shadedFloor = applyLighting(floorColor, floorSlope * floorShadow + (float)noise);

                    float depthRatio = Math.min(1.0f, depth / 25.0f);

                    int waterColorDeep = MapRenderSettings.waterDeepColor;
                    int waterDisplayRGB = blendColorsRGB(waterBaseColor, waterColorDeep, depthRatio * MapRenderSettings.waterDarkeningStrength);

                    int baseAlpha = (waterBaseColor >> 24) & 0xFF;
                    float alphaFactor = MapRenderSettings.baseWaterTransparency + (depthRatio * (1.0f - MapRenderSettings.baseWaterTransparency));
                    int finalAlpha = Math.min(255, (int)(baseAlpha + (255 - baseAlpha) * alphaFactor));

                    int waterRGBA = (finalAlpha << 24) | (waterDisplayRGB & 0xFFFFFF);

                    finalColor = blendPixels(shadedFloor, waterRGBA);
                } else {
                    float slope = calculateSlope(heightMap, mapIdx, CACHE_WIDTH_BLOCKS, h);
                    float shadow = calculateShadow(heightMap, mapIdx, CACHE_WIDTH_BLOCKS, h);

                    float light = slope * shadow;
                    finalColor = applyLighting(waterBaseColor, light + (float)noise);
                }

                pixels[y * TILE_SIZE + x] = 0xFF000000 | (finalColor & 0xFFFFFF);
            }
        }

        img.setRGB(0, 0, TILE_SIZE, TILE_SIZE, pixels, 0, TILE_SIZE);
        overviewCache.put(key, img);
    }

    private float calculateSlope(short[] hMap, int idx, int stride, int h) {
        int hNW = hMap[idx - stride - 1];

        int dH = h - hNW;

        if (dH == 0) return MapRenderSettings.ambientLight;

        float slopeFactor = (float) (Math.signum(dH) * Math.pow(Math.abs(dH), 0.6) * 0.1f * MapRenderSettings.slopeSensitivity);

        if (dH > 0) {
            return Math.min(MapRenderSettings.highlightStrength, MapRenderSettings.ambientLight + slopeFactor);
        } else {
            return Math.max(MapRenderSettings.shadowStrength, MapRenderSettings.ambientLight + slopeFactor);
        }
    }

    private float calculateShadow(short[] hMap, int idx, int stride, int h) {
        int maxSteps = 12;
        for (int i = 1; i <= maxSteps; i++) {
            int sampleIdx = idx - (stride * i) - i;
            if (sampleIdx < 0 || sampleIdx >= hMap.length) break;

            int blockH = hMap[sampleIdx];
            int rayH = h + i;

            if (blockH > rayH) {
                return MapRenderSettings.shadowStrength;
            }
        }
        return 1.0f;
    }

    private int applyLighting(int color, float mult) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = (color & 0xFF);

        r = clamp((int)(r * mult));
        g = clamp((int)(g * mult));
        b = clamp((int)(b * mult));

        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private int blendColorsRGB(int c1, int c2, float ratio) {
        float iRatio = 1.0f - ratio;
        int r = (int) (((c1 >> 16) & 0xFF) * iRatio + ((c2 >> 16) & 0xFF) * ratio);
        int g = (int) (((c1 >> 8) & 0xFF) * iRatio + ((c2 >> 8) & 0xFF) * ratio);
        int b = (int) ((c1 & 0xFF) * iRatio + (c2 & 0xFF) * ratio);
        return (r << 16) | (g << 8) | b;
    }

    private int blendPixels(int dst, int src) {
        int sa = (src >> 24) & 0xFF;
        if (sa == 255) return src;
        if (sa == 0) return dst;

        float alpha = sa / 255.0f;
        float invAlpha = 1.0f - alpha;

        int r = (int)(((src >> 16) & 0xFF) * alpha + ((dst >> 16) & 0xFF) * invAlpha);
        int g = (int)(((src >> 8) & 0xFF) * alpha + ((dst >> 8) & 0xFF) * invAlpha);
        int b = (int)((src & 0xFF) * alpha + (dst & 0xFF) * invAlpha);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int clamp(int val) {
        return val > 255 ? 255 : (Math.max(val, 0));
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
