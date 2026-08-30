package redxax.oxy.remotely.discord;

public final class DiscordRpcBridge {
    public interface Service {
        void start();

        void shutdown();

        void reloadSettings();

        void refreshTrackedInstances();

        void trackInstance(String serverId);

        void setManagerActive();

        void setGlobalSettingsActive();

        void setServerSettingsActive(String serverId);

        void setServerCreationActive();

        void setLocalTerminalActive();

        void setServerActive(String serverId, String viewName);

        void setReSyncStudioActive(String serverId, String serverTitle, String studioName);

        void updateServerMetrics(String serverId, int playerCount, long uptimeMs, double cpuPercent, long memoryBytes, long memoryLimitBytes);

        void markUserActivity();
    }

    private static Service service;

    private DiscordRpcBridge() {
    }

    public static void install(Service value) {
        service = value;
    }

    public static void start() {
        if (service != null) service.start();
    }

    public static void shutdown() {
        if (service != null) service.shutdown();
    }

    public static void reloadSettings() {
        if (service != null) service.reloadSettings();
    }

    public static void refreshTrackedInstances() {
        if (service != null) service.refreshTrackedInstances();
    }

    public static void trackInstance(String serverId) {
        if (service != null) service.trackInstance(serverId);
    }

    public static void setManagerActive() {
        if (service != null) service.setManagerActive();
    }

    public static void setGlobalSettingsActive() {
        if (service != null) service.setGlobalSettingsActive();
    }

    public static void setServerSettingsActive(String serverId) {
        if (service != null) service.setServerSettingsActive(serverId);
    }

    public static void setServerCreationActive() {
        if (service != null) service.setServerCreationActive();
    }

    public static void setLocalTerminalActive() {
        if (service != null) service.setLocalTerminalActive();
    }

    public static void setServerActive(String serverId, String viewName) {
        if (service != null) service.setServerActive(serverId, viewName);
    }

    public static void setReSyncStudioActive(String serverId, String serverTitle, String studioName) {
        if (service != null) service.setReSyncStudioActive(serverId, serverTitle, studioName);
    }

    public static void updateServerMetrics(String serverId, int playerCount, long uptimeMs, double cpuPercent, long memoryBytes, long memoryLimitBytes) {
        if (service != null) service.updateServerMetrics(serverId, playerCount, uptimeMs, cpuPercent, memoryBytes, memoryLimitBytes);
    }

    public static void markUserActivity() {
        if (service != null) service.markUserActivity();
    }
}
