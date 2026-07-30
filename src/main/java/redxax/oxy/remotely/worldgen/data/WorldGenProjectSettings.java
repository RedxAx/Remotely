package redxax.oxy.remotely.worldgen.data;

import restudio.resync.worldgen.contract.WorldGenGenerationMode;
import restudio.resync.worldgen.contract.WorldGenTargetVersion;

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
    private String generationMode = WorldGenGenerationMode.HYBRID.id();
    private String targetVersion = WorldGenTargetVersion.AUTOMATIC;
    private String worldPreset = "overworld";
    private String terrainTemplate = "continental";
    private boolean vanillaBiomesEnabled = true;
    private boolean vanillaFeaturesEnabled = true;
    private boolean vanillaStructuresEnabled = true;
    private boolean vanillaSpawnsEnabled = true;
    private boolean vanillaStructureTerrainSafety = true;
    private int vanillaStructureSampleRadius = 48;
    private int vanillaStructureMaxHeightDelta = 12;
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

    public String getGenerationMode() {
        return WorldGenGenerationMode.resolve(generationMode).id();
    }

    public void setGenerationMode(String generationMode) {
        this.generationMode = WorldGenGenerationMode.resolve(generationMode).id();
    }

    public String getTargetVersion() {
        return targetVersion == null || targetVersion.isBlank() ? WorldGenTargetVersion.AUTOMATIC : targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion == null || targetVersion.isBlank() || WorldGenTargetVersion.AUTOMATIC.equalsIgnoreCase(targetVersion)
            ? WorldGenTargetVersion.AUTOMATIC
            : WorldGenTargetVersion.require(targetVersion).id();
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

    public boolean isVanillaStructureTerrainSafety() {
        return vanillaStructureTerrainSafety;
    }

    public void setVanillaStructureTerrainSafety(boolean vanillaStructureTerrainSafety) {
        this.vanillaStructureTerrainSafety = vanillaStructureTerrainSafety;
    }

    public int getVanillaStructureSampleRadius() {
        return vanillaStructureSampleRadius;
    }

    public void setVanillaStructureSampleRadius(int vanillaStructureSampleRadius) {
        this.vanillaStructureSampleRadius = Math.clamp(vanillaStructureSampleRadius, 0, 128);
    }

    public int getVanillaStructureMaxHeightDelta() {
        return vanillaStructureMaxHeightDelta;
    }

    public void setVanillaStructureMaxHeightDelta(int vanillaStructureMaxHeightDelta) {
        this.vanillaStructureMaxHeightDelta = Math.max(0, vanillaStructureMaxHeightDelta);
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
