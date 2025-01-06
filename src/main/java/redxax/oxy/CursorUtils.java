package redxax.oxy;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CursorUtils {
    private static float cursorOpacity = 1.0f;
    private static boolean cursorFadingOut = true;
    private static long lastCursorBlinkTime = 0;
    private static final long CURSOR_BLINK_INTERVAL = 30;
    private static int cursor = 0xFFd6f264;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static {
        startCursorOpacityUpdater();
    }

    private static void startCursorOpacityUpdater() {
        scheduler.scheduleAtFixedRate(CursorUtils::updateCursorOpacity, 0, CURSOR_BLINK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private static void updateCursorOpacity() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCursorBlinkTime >= CURSOR_BLINK_INTERVAL) {
            lastCursorBlinkTime = currentTime;
            if (cursorFadingOut) {
                cursorOpacity -= 0.1f;
                if (cursorOpacity <= 0.0f) {
                    cursorOpacity = 0.0f;
                    cursorFadingOut = false;
                }
            } else {
                cursorOpacity += 0.1f;
                if (cursorOpacity >= 1.0f) {
                    cursorOpacity = 1.0f;
                    cursorFadingOut = true;
                }
            }
        }
    }

    public static int blendColor() {
        int color = cursor;
        float opacity = cursorOpacity;
        int a = (int) ((color >> 24 & 0xFF) * opacity);
        int r = (color >> 16 & 0xFF);
        int g = (color >> 8 & 0xFF);
        int b = (color & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void setCursor(int newCursor) {
        cursor = newCursor;
    }
}