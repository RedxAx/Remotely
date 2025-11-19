package redxax.oxy.remotely.util;

import static redxax.oxy.remotely.config.Config.globalCursorColor;
import static restudio.rescreen.config.Config.deltaTime;

public class CursorUtils {
    private static float cursorOpacity = 1.0f;
    private static boolean cursorFadingOut = true;

    public static void tick() {
        float blinkSpeed = 2.0f;
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

    public static int blendColor() {
        float opacity = Math.max(0.0f, Math.min(1.0f, cursorOpacity));
        int a = (int) ((globalCursorColor >> 24 & 0xFF) * opacity);
        int r = (globalCursorColor >> 16 & 0xFF);
        int g = (globalCursorColor >> 8 & 0xFF);
        int b = (globalCursorColor & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
