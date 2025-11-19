package redxax.oxy.remotely.ui.widgets.worldmap;

import java.util.Arrays;

public class ChunkData {
    private final short[] heights = new short[256];
    private final String[] ids = new String[256];
    private final String[] biomes = new String[256];

    public ChunkData() {
        Arrays.fill(biomes, "minecraft:plains");
    }

    public void setBlock(int x, int z, String id, int y, String biomeId) {
        int idx = z * 16 + x;
        ids[idx] = id;
        heights[idx] = (short) y;
        biomes[idx] = biomeId != null ? biomeId : "minecraft:plains";
    }

    public String getBlockId(int x, int z) {
        return ids[z * 16 + x];
    }

    public int getHeight(int x, int z) {
        return heights[z * 16 + x];
    }

    public String getBiomeId(int x, int z) {
        return biomes[z * 16 + x];
    }
}