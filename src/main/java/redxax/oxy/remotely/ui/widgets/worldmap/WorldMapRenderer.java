package redxax.oxy.remotely.ui.widgets.worldmap;

import restudio.rescreen.platform.IDrawContext;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public class WorldMapRenderer {

    private final ChunkLoader chunkLoader;
    private final MapTileCache tileCache;

    private static final int REGION_SIZE_BLOCKS = 512;

    public WorldMapRenderer(Path worldFolder, AssetManager assetManager) {
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

        float worldCenterX = (worldLeft + worldRight) / 2f;
        float worldCenterZ = (worldTop + worldBottom) / 2f;
        int centerRx = (int) Math.floor(worldCenterX / REGION_SIZE_BLOCKS);
        int centerRz = (int) Math.floor(worldCenterZ / REGION_SIZE_BLOCKS);
        renderMap(ctx, rXMin, rZMin, rXMax, rZMax, centerRx, centerRz);

    }

    private void renderMap(IDrawContext ctx, int rXMin, int rZMin, int rXMax, int rZMax, int centerRx, int centerRz) {
        for (int rx = rXMin; rx <= rXMax; rx++) {
            for (int rz = rZMin; rz <= rZMax; rz++) {
                BufferedImage tile = tileCache.getTile(rx, rz, centerRx, centerRz);
                if (tile != null) {
                    ctx.drawPixelArt(tile, rx * REGION_SIZE_BLOCKS, rz * REGION_SIZE_BLOCKS, REGION_SIZE_BLOCKS + 0.4f, REGION_SIZE_BLOCKS + 0.4f);
                }
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

    public void clearCache() {
        tileCache.clear();
    }

    public void close() {
        chunkLoader.shutdown();
        tileCache.shutdown();
    }
}
