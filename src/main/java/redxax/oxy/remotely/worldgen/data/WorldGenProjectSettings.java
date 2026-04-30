package redxax.oxy.remotely.worldgen.data;

import java.util.HashMap;
import java.util.Map;

public class WorldGenProjectSettings {
    private String seedPolicy = "preview";
    private int minY = -64;
    private int maxY = 320;
    private int seaLevel = 63;
    private String defaultBlock = "minecraft:stone";
    private String defaultFluid = "minecraft:water";
    private String datapackNamespace = "resync_worldgen";
    private String generatorBackend = "datapack";
    private String worldPreset = "overworld";
    private String terrainTemplate = "continental";
    private boolean vanillaBiomesEnabled;
    private boolean vanillaFeaturesEnabled;
    private boolean vanillaStructuresEnabled;
    private boolean vanillaSpawnsEnabled;
    private Map<String, Boolean> biomeVanillaFeatureOverrides = new HashMap<>();
    private String previewEnvironment = "NORMAL";
    private String activePreviewPlayer = "";

    public String getSeedPolicy() {
        return seedPolicy;
    }

    public void setSeedPolicy(String seedPolicy) {
        this.seedPolicy = seedPolicy;
    }

    public int getMinY() {
        return minY;
    }

    public void setMinY(int minY) {
        this.minY = minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public void setMaxY(int maxY) {
        this.maxY = maxY;
    }

    public int getSeaLevel() {
        return seaLevel;
    }

    public void setSeaLevel(int seaLevel) {
        this.seaLevel = seaLevel;
    }

    public String getDefaultBlock() {
        return defaultBlock;
    }

    public void setDefaultBlock(String defaultBlock) {
        this.defaultBlock = defaultBlock;
    }

    public String getDefaultFluid() {
        return defaultFluid;
    }

    public void setDefaultFluid(String defaultFluid) {
        this.defaultFluid = defaultFluid;
    }

    public String getDatapackNamespace() {
        return datapackNamespace;
    }

    public void setDatapackNamespace(String datapackNamespace) {
        this.datapackNamespace = datapackNamespace;
    }

    public String getGeneratorBackend() {
        return generatorBackend;
    }

    public void setGeneratorBackend(String generatorBackend) {
        this.generatorBackend = generatorBackend;
    }

    public String getWorldPreset() {
        return worldPreset;
    }

    public void setWorldPreset(String worldPreset) {
        this.worldPreset = worldPreset;
    }

    public String getTerrainTemplate() {
        return terrainTemplate;
    }

    public void setTerrainTemplate(String terrainTemplate) {
        this.terrainTemplate = terrainTemplate;
    }

    public boolean isVanillaBiomesEnabled() {
        return vanillaBiomesEnabled;
    }

    public void setVanillaBiomesEnabled(boolean vanillaBiomesEnabled) {
        this.vanillaBiomesEnabled = vanillaBiomesEnabled;
    }

    public boolean isVanillaFeaturesEnabled() {
        return vanillaFeaturesEnabled;
    }

    public void setVanillaFeaturesEnabled(boolean vanillaFeaturesEnabled) {
        this.vanillaFeaturesEnabled = vanillaFeaturesEnabled;
    }

    public boolean isVanillaStructuresEnabled() {
        return vanillaStructuresEnabled;
    }

    public void setVanillaStructuresEnabled(boolean vanillaStructuresEnabled) {
        this.vanillaStructuresEnabled = vanillaStructuresEnabled;
    }

    public boolean isVanillaSpawnsEnabled() {
        return vanillaSpawnsEnabled;
    }

    public void setVanillaSpawnsEnabled(boolean vanillaSpawnsEnabled) {
        this.vanillaSpawnsEnabled = vanillaSpawnsEnabled;
    }

    public Map<String, Boolean> getBiomeVanillaFeatureOverrides() {
        return biomeVanillaFeatureOverrides;
    }

    public void setBiomeVanillaFeatureOverrides(Map<String, Boolean> biomeVanillaFeatureOverrides) {
        this.biomeVanillaFeatureOverrides = biomeVanillaFeatureOverrides != null ? biomeVanillaFeatureOverrides : new HashMap<>();
    }

    public String getPreviewEnvironment() {
        return previewEnvironment;
    }

    public void setPreviewEnvironment(String previewEnvironment) {
        this.previewEnvironment = previewEnvironment;
    }

    public String getActivePreviewPlayer() {
        return activePreviewPlayer;
    }

    public void setActivePreviewPlayer(String activePreviewPlayer) {
        this.activePreviewPlayer = activePreviewPlayer;
    }
}
