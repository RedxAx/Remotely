package redxax.oxy.remotely.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static restudio.rescreen.config.Config.deltaTime;
import static redxax.oxy.remotely.config.Config.globalCursorColor;

public class CursorUtils {
    private static float cursorOpacity = 1.0f;
    private static boolean cursorFadingOut = true;
    private static long lastCursorBlinkTime = 0;
    private static final long CURSOR_BLINK_INTERVAL = 30;
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

            float blinkSpeed = 80.0f;
            float deltaChange = blinkSpeed * deltaTime;

            if (cursorFadingOut) {
                cursorOpacity -= deltaChange;
                if (cursorOpacity <= 0.0f) {
                    cursorOpacity = 0.0f;
                    cursorFadingOut = false;
                }
            } else {
                cursorOpacity += deltaChange;
                if (cursorOpacity >= 1.0f) {
                    cursorOpacity = 1.0f;
                    cursorFadingOut = true;
                }
            }
        }
    }

    public static int blendColor() {
        float opacity = cursorOpacity;
        int a = (int) ((globalCursorColor >> 24 & 0xFF) * opacity);
        int r = (globalCursorColor >> 16 & 0xFF);
        int g = (globalCursorColor >> 8 & 0xFF);
        int b = (globalCursorColor & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}