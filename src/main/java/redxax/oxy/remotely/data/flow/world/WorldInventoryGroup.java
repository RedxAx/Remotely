package redxax.oxy.remotely.data.flow.world;

import java.util.ArrayList;
import java.util.List;

public class WorldInventoryGroup {
    private String groupId;
    private String displayName;
    private List<String> worlds = new ArrayList<>();
    private boolean shareInventory = true;
    private boolean shareArmor = true;
    private boolean shareOffhand = true;
    private boolean shareEnderChest = true;
    private boolean shareHealth = true;
    private boolean shareHunger = true;
    private boolean shareExperience = true;
    private boolean shareGameMode = true;
    private boolean sharePotionEffects = true;
    private boolean shareLastLocation = true;
    private boolean shareBedSpawn = true;
    private long updatedAt;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getWorlds() {
        return worlds == null ? List.of() : worlds;
    }

    public void setWorlds(List<String> worlds) {
        this.worlds = worlds == null ? new ArrayList<>() : new ArrayList<>(worlds);
    }

    public boolean isShareInventory() {
        return shareInventory;
    }

    public void setShareInventory(boolean shareInventory) {
        this.shareInventory = shareInventory;
    }

    public boolean isShareArmor() {
        return shareArmor;
    }

    public void setShareArmor(boolean shareArmor) {
        this.shareArmor = shareArmor;
    }

    public boolean isShareOffhand() {
        return shareOffhand;
    }

    public void setShareOffhand(boolean shareOffhand) {
        this.shareOffhand = shareOffhand;
    }

    public boolean isShareEnderChest() {
        return shareEnderChest;
    }

    public void setShareEnderChest(boolean shareEnderChest) {
        this.shareEnderChest = shareEnderChest;
    }

    public boolean isShareHealth() {
        return shareHealth;
    }

    public void setShareHealth(boolean shareHealth) {
        this.shareHealth = shareHealth;
    }

    public boolean isShareHunger() {
        return shareHunger;
    }

    public void setShareHunger(boolean shareHunger) {
        this.shareHunger = shareHunger;
    }

    public boolean isShareExperience() {
        return shareExperience;
    }

    public void setShareExperience(boolean shareExperience) {
        this.shareExperience = shareExperience;
    }

    public boolean isShareGameMode() {
        return shareGameMode;
    }

    public void setShareGameMode(boolean shareGameMode) {
        this.shareGameMode = shareGameMode;
    }

    public boolean isSharePotionEffects() {
        return sharePotionEffects;
    }

    public void setSharePotionEffects(boolean sharePotionEffects) {
        this.sharePotionEffects = sharePotionEffects;
    }

    public boolean isShareLastLocation() {
        return shareLastLocation;
    }

    public void setShareLastLocation(boolean shareLastLocation) {
        this.shareLastLocation = shareLastLocation;
    }

    public boolean isShareBedSpawn() {
        return shareBedSpawn;
    }

    public void setShareBedSpawn(boolean shareBedSpawn) {
        this.shareBedSpawn = shareBedSpawn;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
