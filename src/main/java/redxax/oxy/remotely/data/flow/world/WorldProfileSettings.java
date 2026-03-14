package redxax.oxy.remotely.data.flow.world;

public class WorldProfileSettings {
    private String alias;
    private boolean hidden;
    private String accessPermission;
    private String bypassPermission;
    private String respawnWorld;
    private boolean forceGameMode;
    private String gameMode;
    private boolean customSpawnEnabled;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;
    private boolean entryFeeEnabled;
    private double entryFee;
    private boolean pvpEnabled = true;
    private boolean keepSpawnLoaded = true;
    private boolean autoSaveEnabled = true;
    private boolean animalSpawnsEnabled = true;
    private boolean monsterSpawnsEnabled = true;
    private boolean hungerEnabled = true;
    private boolean autoHealEnabled = true;
    private boolean bedRespawnEnabled = true;
    private boolean anchorRespawnEnabled = true;
    private String arrivalMessage;
    private String denyMessage;
    private String inventoryGroupId;
    private String linkedNetherWorld;
    private String linkedEndWorld;
    private String linkedOverworld;
    private double netherScale = 8.0;
    private double endScale = 1.0;
    private boolean autoLinkNetherPortal = true;
    private boolean autoLinkEndPortal = true;
    private boolean nonLivingEntitySpawnsEnabled = true;

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String getAccessPermission() {
        return accessPermission;
    }

    public void setAccessPermission(String accessPermission) {
        this.accessPermission = accessPermission;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }

    public void setBypassPermission(String bypassPermission) {
        this.bypassPermission = bypassPermission;
    }

    public String getRespawnWorld() {
        return respawnWorld;
    }

    public void setRespawnWorld(String respawnWorld) {
        this.respawnWorld = respawnWorld;
    }

    public boolean isForceGameMode() {
        return forceGameMode;
    }

    public void setForceGameMode(boolean forceGameMode) {
        this.forceGameMode = forceGameMode;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public boolean isCustomSpawnEnabled() {
        return customSpawnEnabled;
    }

    public void setCustomSpawnEnabled(boolean customSpawnEnabled) {
        this.customSpawnEnabled = customSpawnEnabled;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public void setSpawnX(double spawnX) {
        this.spawnX = spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public void setSpawnY(double spawnY) {
        this.spawnY = spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public void setSpawnZ(double spawnZ) {
        this.spawnZ = spawnZ;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public void setSpawnYaw(float spawnYaw) {
        this.spawnYaw = spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    public void setSpawnPitch(float spawnPitch) {
        this.spawnPitch = spawnPitch;
    }

    public boolean isEntryFeeEnabled() {
        return entryFeeEnabled;
    }

    public void setEntryFeeEnabled(boolean entryFeeEnabled) {
        this.entryFeeEnabled = entryFeeEnabled;
    }

    public double getEntryFee() {
        return entryFee;
    }

    public void setEntryFee(double entryFee) {
        this.entryFee = entryFee;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public boolean isKeepSpawnLoaded() {
        return keepSpawnLoaded;
    }

    public void setKeepSpawnLoaded(boolean keepSpawnLoaded) {
        this.keepSpawnLoaded = keepSpawnLoaded;
    }

    public boolean isAutoSaveEnabled() {
        return autoSaveEnabled;
    }

    public void setAutoSaveEnabled(boolean autoSaveEnabled) {
        this.autoSaveEnabled = autoSaveEnabled;
    }

    public boolean isAnimalSpawnsEnabled() {
        return animalSpawnsEnabled;
    }

    public void setAnimalSpawnsEnabled(boolean animalSpawnsEnabled) {
        this.animalSpawnsEnabled = animalSpawnsEnabled;
    }

    public boolean isMonsterSpawnsEnabled() {
        return monsterSpawnsEnabled;
    }

    public void setMonsterSpawnsEnabled(boolean monsterSpawnsEnabled) {
        this.monsterSpawnsEnabled = monsterSpawnsEnabled;
    }

    public boolean isHungerEnabled() {
        return hungerEnabled;
    }

    public void setHungerEnabled(boolean hungerEnabled) {
        this.hungerEnabled = hungerEnabled;
    }

    public boolean isAutoHealEnabled() {
        return autoHealEnabled;
    }

    public void setAutoHealEnabled(boolean autoHealEnabled) {
        this.autoHealEnabled = autoHealEnabled;
    }

    public boolean isBedRespawnEnabled() {
        return bedRespawnEnabled;
    }

    public void setBedRespawnEnabled(boolean bedRespawnEnabled) {
        this.bedRespawnEnabled = bedRespawnEnabled;
    }

    public boolean isAnchorRespawnEnabled() {
        return anchorRespawnEnabled;
    }

    public void setAnchorRespawnEnabled(boolean anchorRespawnEnabled) {
        this.anchorRespawnEnabled = anchorRespawnEnabled;
    }

    public String getArrivalMessage() {
        return arrivalMessage;
    }

    public void setArrivalMessage(String arrivalMessage) {
        this.arrivalMessage = arrivalMessage;
    }

    public String getDenyMessage() {
        return denyMessage;
    }

    public void setDenyMessage(String denyMessage) {
        this.denyMessage = denyMessage;
    }

    public String getInventoryGroupId() {
        return inventoryGroupId;
    }

    public void setInventoryGroupId(String inventoryGroupId) {
        this.inventoryGroupId = inventoryGroupId;
    }

    public String getLinkedNetherWorld() {
        return linkedNetherWorld;
    }

    public void setLinkedNetherWorld(String linkedNetherWorld) {
        this.linkedNetherWorld = linkedNetherWorld;
    }

    public String getLinkedEndWorld() {
        return linkedEndWorld;
    }

    public void setLinkedEndWorld(String linkedEndWorld) {
        this.linkedEndWorld = linkedEndWorld;
    }

    public String getLinkedOverworld() {
        return linkedOverworld;
    }

    public void setLinkedOverworld(String linkedOverworld) {
        this.linkedOverworld = linkedOverworld;
    }

    public double getNetherScale() {
        return netherScale > 0.0 ? netherScale : 8.0;
    }

    public void setNetherScale(double netherScale) {
        this.netherScale = netherScale;
    }

    public double getEndScale() {
        return endScale > 0.0 ? endScale : 1.0;
    }

    public void setEndScale(double endScale) {
        this.endScale = endScale;
    }

    public boolean isAutoLinkNetherPortal() {
        return autoLinkNetherPortal;
    }

    public void setAutoLinkNetherPortal(boolean autoLinkNetherPortal) {
        this.autoLinkNetherPortal = autoLinkNetherPortal;
    }

    public boolean isAutoLinkEndPortal() {
        return autoLinkEndPortal;
    }

    public void setAutoLinkEndPortal(boolean autoLinkEndPortal) {
        this.autoLinkEndPortal = autoLinkEndPortal;
    }

    public boolean isNonLivingEntitySpawnsEnabled() {
        return nonLivingEntitySpawnsEnabled;
    }

    public void setNonLivingEntitySpawnsEnabled(boolean nonLivingEntitySpawnsEnabled) {
        this.nonLivingEntitySpawnsEnabled = nonLivingEntitySpawnsEnabled;
    }
}
