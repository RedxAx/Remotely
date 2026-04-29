package redxax.oxy.remotely.worldgen.data;

public enum WorldGenStage {
    TERRAIN("Terrain"),
    BIOME("Biomes"),
    SURFACE("Surface"),
    CAVE("Caves"),
    FEATURE("Features"),
    STRUCTURE("Structures"),
    SPAWN("Spawns");

    private final String displayName;

    WorldGenStage(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
