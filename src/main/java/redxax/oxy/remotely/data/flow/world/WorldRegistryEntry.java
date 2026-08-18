package redxax.oxy.remotely.data.flow.world;

import java.util.Map;

public class WorldRegistryEntry {
    private String worldName;
    private String environment;
    private String generator;
    private String generatorConfig;
    private String difficulty;
    private boolean loaded;
    private boolean isolatedPlayerState;
    private boolean timeLockEnabled;
    private long lockedTime;
    private boolean weatherLockEnabled;
    private boolean lockedStorm;
    private boolean lockedThundering;
    private WorldProfileSettings profileSettings;
    private Map<String, String> gameRules;
    private long updatedAt;

    public String getWorldName() {
        return worldName;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getGenerator() {
        return generator;
    }

    public String getGeneratorConfig() {
        return generatorConfig;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isIsolatedPlayerState() {
        return isolatedPlayerState;
    }

    public boolean isTimeLockEnabled() {
        return timeLockEnabled;
    }

    public long getLockedTime() {
        return lockedTime;
    }

    public boolean isWeatherLockEnabled() {
        return weatherLockEnabled;
    }

    public boolean isLockedStorm() {
        return lockedStorm;
    }

    public boolean isLockedThundering() {
        return lockedThundering;
    }

    public WorldProfileSettings getProfileSettings() {
        return profileSettings == null ? new WorldProfileSettings() : profileSettings;
    }

    public Map<String, String> getGameRules() {
        return gameRules == null ? Map.of() : gameRules;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setWorldName(String worldName) { this.worldName = worldName; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public void setGenerator(String generator) { this.generator = generator; }
    public void setGeneratorConfig(String generatorConfig) { this.generatorConfig = generatorConfig; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }
    public void setIsolatedPlayerState(boolean isolatedPlayerState) { this.isolatedPlayerState = isolatedPlayerState; }
    public void setTimeLockEnabled(boolean timeLockEnabled) { this.timeLockEnabled = timeLockEnabled; }
    public void setLockedTime(long lockedTime) { this.lockedTime = lockedTime; }
    public void setWeatherLockEnabled(boolean weatherLockEnabled) { this.weatherLockEnabled = weatherLockEnabled; }
    public void setLockedStorm(boolean lockedStorm) { this.lockedStorm = lockedStorm; }
    public void setLockedThundering(boolean lockedThundering) { this.lockedThundering = lockedThundering; }
    public void setProfileSettings(WorldProfileSettings profileSettings) { this.profileSettings = profileSettings; }
    public void setGameRules(Map<String, String> gameRules) { this.gameRules = gameRules; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
