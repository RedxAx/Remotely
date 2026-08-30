package redxax.oxy.remotely.data.flow;

@FunctionalInterface
public interface ReSyncConnectionNotificationSink {
    void show(String title, String message, ReSyncNotificationLevel level);

    static ReSyncConnectionNotificationSink noop() {
        return (title, message, level) -> {
        };
    }
}
