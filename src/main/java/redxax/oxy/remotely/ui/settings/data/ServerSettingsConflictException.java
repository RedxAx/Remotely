package redxax.oxy.remotely.ui.settings.data;

public final class ServerSettingsConflictException extends IllegalStateException {
    private final String relativePath;
    private final String key;
    private final String baselineValue;
    private final String latestValue;

    public ServerSettingsConflictException(String relativePath, String key, String baselineValue, String latestValue) {
        super("Configuration changed while editing " + relativePath + " at " + key);
        this.relativePath = relativePath;
        this.key = key;
        this.baselineValue = baselineValue;
        this.latestValue = latestValue;
    }

    public String relativePath() {
        return relativePath;
    }

    public String key() {
        return key;
    }

    public String baselineValue() {
        return baselineValue;
    }

    public String latestValue() {
        return latestValue;
    }
}
