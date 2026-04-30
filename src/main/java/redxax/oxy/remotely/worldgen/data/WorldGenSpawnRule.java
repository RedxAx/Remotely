package redxax.oxy.remotely.worldgen.data;

import java.util.ArrayList;
import java.util.List;

public class WorldGenSpawnRule {
    private String entityType = "minecraft:zombie";
    private int weight = 10;
    private int minGroup = 1;
    private int maxGroup = 4;
    private String category = "monster";
    private List<String> biomeFilters = new ArrayList<>();
    private int minY = -64;
    private int maxY = 320;
    private String blockBelow = "";
    private int minLight;
    private int maxLight = 15;
    private String time = "any";
    private String weather = "any";

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public int getMinGroup() { return minGroup; }
    public void setMinGroup(int minGroup) { this.minGroup = minGroup; }
    public int getMaxGroup() { return maxGroup; }
    public void setMaxGroup(int maxGroup) { this.maxGroup = maxGroup; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getBiomeFilters() { return biomeFilters; }
    public void setBiomeFilters(List<String> biomeFilters) { this.biomeFilters = biomeFilters != null ? biomeFilters : new ArrayList<>(); }
    public int getMinY() { return minY; }
    public void setMinY(int minY) { this.minY = minY; }
    public int getMaxY() { return maxY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
    public String getBlockBelow() { return blockBelow; }
    public void setBlockBelow(String blockBelow) { this.blockBelow = blockBelow; }
    public int getMinLight() { return minLight; }
    public void setMinLight(int minLight) { this.minLight = minLight; }
    public int getMaxLight() { return maxLight; }
    public void setMaxLight(int maxLight) { this.maxLight = maxLight; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }
}
