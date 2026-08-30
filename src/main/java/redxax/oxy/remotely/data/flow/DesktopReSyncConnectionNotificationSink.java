package redxax.oxy.remotely.data.flow;

import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

public final class DesktopReSyncConnectionNotificationSink implements ReSyncConnectionNotificationSink {
    @Override
    public void show(String title, String message, ReSyncNotificationLevel level) {
        ScreenManager.getInstance().execute(() -> new Notification(title, message, switch (level) {
            case WARN -> Notification.Type.WARN;
            case ERROR -> Notification.Type.ERROR;
            case SUCCESS -> Notification.Type.SUCCESS;
            default -> Notification.Type.INFO;
        }));
    }
}
