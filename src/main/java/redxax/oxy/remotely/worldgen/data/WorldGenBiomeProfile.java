package redxax.oxy.remotely.worldgen.data;

import java.util.ArrayList;
import java.util.List;

public class WorldGenBiomeProfile {
    private String id = "";
    private String displayName = "";
    private WorldGenBiomeProfileMode mode = WorldGenBiomeProfileMode.CUSTOM;
    private String vanillaBaseBiome = "minecraft:plains";
    private float temperature = 0.5f;
    private float humidity = 0.5f;
    private float continentalness;
    private float erosion;
    private float weirdness;
    private String surfaceReference = "";
    private boolean keepVanillaFeatures;
    private boolean keepVanillaStructures;
    private boolean keepVanillaSpawns;
    private List<WorldGenSpawnRule> spawnRules = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public WorldGenBiomeProfileMode getMode() { return mode; }
    public void setMode(WorldGenBiomeProfileMode mode) { this.mode = mode != null ? mode : WorldGenBiomeProfileMode.CUSTOM; }
    public String getVanillaBaseBiome() { return vanillaBaseBiome; }
    public void setVanillaBaseBiome(String vanillaBaseBiome) { this.vanillaBaseBiome = vanillaBaseBiome; }
    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; }
    public float getHumidity() { return humidity; }
    public void setHumidity(float humidity) { this.humidity = humidity; }
    public float getContinentalness() { return continentalness; }
    public void setContinentalness(float continentalness) { this.continentalness = continentalness; }
    public float getErosion() { return erosion; }
    public void setErosion(float erosion) { this.erosion = erosion; }
    public float getWeirdness() { return weirdness; }
    public void setWeirdness(float weirdness) { this.weirdness = weirdness; }
    public String getSurfaceReference() { return surfaceReference; }
    public void setSurfaceReference(String surfaceReference) { this.surfaceReference = surfaceReference; }
    public boolean isKeepVanillaFeatures() { return keepVanillaFeatures; }
    public void setKeepVanillaFeatures(boolean keepVanillaFeatures) { this.keepVanillaFeatures = keepVanillaFeatures; }
    public boolean isKeepVanillaStructures() { return keepVanillaStructures; }
    public void setKeepVanillaStructures(boolean keepVanillaStructures) { this.keepVanillaStructures = keepVanillaStructures; }
    public boolean isKeepVanillaSpawns() { return keepVanillaSpawns; }
    public void setKeepVanillaSpawns(boolean keepVanillaSpawns) { this.keepVanillaSpawns = keepVanillaSpawns; }
    public List<WorldGenSpawnRule> getSpawnRules() { return spawnRules; }
    public void setSpawnRules(List<WorldGenSpawnRule> spawnRules) { this.spawnRules = spawnRules != null ? spawnRules : new ArrayList<>(); }
}
