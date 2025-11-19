package redxax.oxy.remotely.ui.widgets.worldmap;

import restudio.rescreen.platform.IDrawContext;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public class WorldMapRenderer {

    private final ChunkLoader chunkLoader;
    private final MapTileCache tileCache;
    private final AssetManager assetManager;
    
    private static final int REGION_SIZE_BLOCKS = 512;

    public WorldMapRenderer(Path worldFolder, AssetManager assetManager) {
        this.assetManager = assetManager;
        this.chunkLoader = new ChunkLoader(worldFolder);
        this.tileCache = new MapTileCache(chunkLoader, assetManager);
    }

    public void render(IDrawContext ctx, float panX, float panY, float zoom, int screenW, int screenH) {

        float centerX = screenW / 2.0f;
        float centerY = screenH / 2.0f;
        
        float worldLeft = (0 - centerX) / zoom + centerX - panX;
        float worldTop = (0 - centerY) / zoom + centerY - panY;
        float worldRight = (screenW - centerX) / zoom + centerX - panX;
        float worldBottom = (screenH - centerY) / zoom + centerY - panY;

        int rXMin = (int) Math.floor(worldLeft / REGION_SIZE_BLOCKS);
        int rZMin = (int) Math.floor(worldTop / REGION_SIZE_BLOCKS);
        int rXMax = (int) Math.floor(worldRight / REGION_SIZE_BLOCKS);
        int rZMax = (int) Math.floor(worldBottom / REGION_SIZE_BLOCKS);

        if (zoom < 1.5f) {
            float worldCenterX = (worldLeft + worldRight) / 2f;
            float worldCenterZ = (worldTop + worldBottom) / 2f;
            int centerRx = (int) Math.floor(worldCenterX / REGION_SIZE_BLOCKS);
            int centerRz = (int) Math.floor(worldCenterZ / REGION_SIZE_BLOCKS);
            renderOverview(ctx, rXMin, rZMin, rXMax, rZMax, centerRx, centerRz);
        } else {
            renderDetail(ctx, worldLeft, worldTop, worldRight, worldBottom);
        }
    }

    private void renderOverview(IDrawContext ctx, int rXMin, int rZMin, int rXMax, int rZMax, int centerRx, int centerRz) {
        for (int rx = rXMin; rx <= rXMax; rx++) {
            for (int rz = rZMin; rz <= rZMax; rz++) {
                BufferedImage tile = tileCache.getOverviewTile(rx, rz, centerRx, centerRz);
                if (tile != null) {
                    ctx.drawPixelArt(tile, rx * REGION_SIZE_BLOCKS, rz * REGION_SIZE_BLOCKS, REGION_SIZE_BLOCKS + 0.4f, REGION_SIZE_BLOCKS + 0.4f);
                }
            }
        }
    }

    private void renderDetail(IDrawContext ctx, float wLeft, float wTop, float wRight, float wBottom) {
        int cXMin = (int) Math.floor(wLeft / 16.0);
        int cZMin = (int) Math.floor(wTop / 16.0);
        int cXMax = (int) Math.floor(wRight / 16.0);
        int cZMax = (int) Math.floor(wBottom / 16.0);

        for (int cx = cXMin; cx <= cXMax; cx++) {
            for (int cz = cZMin; cz <= cZMax; cz++) {
                ChunkData chunk = chunkLoader.getChunk(cx, cz);
                if (chunk != null) {
                    renderChunkBlocks(ctx, cx, cz, chunk);
                }
            }
        }
    }

    private void renderChunkBlocks(IDrawContext ctx, int cx, int cz, ChunkData chunk) {
        int[] colors = new int[256];
        boolean[] valid = new boolean[256];
        boolean[] visited = new boolean[256];

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                String blockId = chunk.getBlockId(x, z);
                if (blockId == null || blockId.equals("minecraft:air") || blockId.equals("minecraft:cave_air")) {
                    continue;
                }
                String biomeId = chunk.getBiomeId(x, z);
                
                int height = chunk.getHeight(x, z);
                int northHeight = chunk.getHeight(x, Math.max(0, z - 1));
                float brightness = 1.0f;
                if (height < northHeight) brightness = 0.75f;
                else if (height > northHeight) brightness = 1.15f;

                String bN = chunk.getBiomeId(Math.max(0, x - 1), z);
                String bS = chunk.getBiomeId(Math.min(15, x + 1), z);
                String bW = chunk.getBiomeId(x, Math.max(0, z - 1));
                String bE = chunk.getBiomeId(x, Math.min(15, z + 1));
                int color = assetManager.getBlockColorBlended(blockId, biomeId, new String[] { bN, bS, bW, bE });
                
                if (brightness != 1.0f) {
                    color = applyShade(color, brightness);
                }
                colors[z * 16 + x] = color;
                valid[z * 16 + x] = true;
            }
        }

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (!valid[z * 16 + x] || visited[z * 16 + x]) continue;
                
                int color = colors[z * 16 + x];
                int w = 1;
                while (x + w < 16 && valid[z * 16 + (x + w)] && !visited[z * 16 + (x + w)] && colors[z * 16 + (x + w)] == color) {
                    w++;
                }
                
                int h = 1;
                boolean canExpandY = true;
                while (z + h < 16 && canExpandY) {
                    for (int k = 0; k < w; k++) {
                        int nextIdx = (z + h) * 16 + (x + k);
                        if (!valid[nextIdx] || visited[nextIdx] || colors[nextIdx] != color) {
                            canExpandY = false;
                            break;
                        }
                    }
                    if (canExpandY) h++;
                }

                for (int dy = 0; dy < h; dy++) {
                    for (int dx = 0; dx < w; dx++) {
                        visited[(z + dy) * 16 + (x + dx)] = true;
                    }
                }

                float bx = cx * 16 + x;
                float bz = cz * 16 + z;
                ctx.fill((int) bx, (int) bz, (int) (bx + w), (int) (bz + h), color);
            }
        }
    }

    public String[] getBlockAndBiomeAt(double worldX, double worldZ) {
        int wx = (int) Math.floor(worldX);
        int wz = (int) Math.floor(worldZ);
        int cx = (int) Math.floor(wx / 16.0);
        int cz = (int) Math.floor(wz / 16.0);
        ChunkData chunk = chunkLoader.getChunk(cx, cz);
        if (chunk == null) return null;
        int lx = Math.floorMod(wx, 16);
        int lz = Math.floorMod(wz, 16);
        String blockId = chunk.getBlockId(lx, lz);
        String biomeId = chunk.getBiomeId(lx, lz);
        return new String[] { blockId, biomeId };
    }
    
    private int applyShade(int color, float mult) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * mult));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * mult));
        int b = Math.min(255, (int) ((color & 0xFF) * mult));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void clearCache() {
        tileCache.clear();
    }

    public void close() {
        chunkLoader.shutdown();
        tileCache.shutdown();
    }
}