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
}
