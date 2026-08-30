package redxax.oxy.remotely.data.flow;

public final class ReSyncClientVersion {
    public static final String CURRENT = "2.1.0";

    private ReSyncClientVersion() {
    }

    public static String resolve(String value) {
        return value == null || value.isBlank() ? CURRENT : value.trim();
    }
}
