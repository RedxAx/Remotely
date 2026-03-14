package redxax.oxy.remotely.data.flow.world;

public class WorldDashboardEntry {
    private String worldName;
    private String status;
    private int playerCount;
    private String environment;
    private boolean loaded;
    private String difficulty;
    private boolean isolatedPlayerState;
    private boolean timeLockEnabled;
    private boolean weatherLockEnabled;
    private String alias;
    private boolean hidden;
    private boolean forceGameMode;
    private String gameMode;
    private boolean entryFeeEnabled;
    private double entryFee;
    private boolean pvpEnabled;
    private boolean keepSpawnLoaded;
    private boolean autoSaveEnabled;
    private boolean animalSpawnsEnabled;
    private boolean monsterSpawnsEnabled;
    private boolean hungerEnabled;
    private boolean autoHealEnabled;
    private boolean bedRespawnEnabled;
    private boolean anchorRespawnEnabled;

    public String getWorldName() {
        return worldName;
    }

    public String getStatus() {
        return status;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public boolean isIsolatedPlayerState() {
        return isolatedPlayerState;
    }

    public boolean isTimeLockEnabled() {
        return timeLockEnabled;
    }

    public boolean isWeatherLockEnabled() {
        return weatherLockEnabled;
    }

    public String getAlias() {
        return alias;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isForceGameMode() {
        return forceGameMode;
    }

    public String getGameMode() {
        return gameMode;
    }

    public boolean isEntryFeeEnabled() {
        return entryFeeEnabled;
    }

    public double getEntryFee() {
        return entryFee;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public boolean isKeepSpawnLoaded() {
        return keepSpawnLoaded;
    }

    public boolean isAutoSaveEnabled() {
        return autoSaveEnabled;
    }

    public boolean isAnimalSpawnsEnabled() {
        return animalSpawnsEnabled;
    }

    public boolean isMonsterSpawnsEnabled() {
        return monsterSpawnsEnabled;
    }

    public boolean isHungerEnabled() {
        return hungerEnabled;
    }

    public boolean isAutoHealEnabled() {
        return autoHealEnabled;
    }

    public boolean isBedRespawnEnabled() {
        return bedRespawnEnabled;
    }

    public boolean isAnchorRespawnEnabled() {
        return anchorRespawnEnabled;
    }
}
