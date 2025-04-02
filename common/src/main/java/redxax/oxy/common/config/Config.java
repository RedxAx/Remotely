package redxax.oxy.common.config;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class Config {
    public static boolean shadow = true;
    public static boolean wallpaper = false;
    public static boolean isDev = false;
    public static boolean background = false;
    public static BufferedImage windowsBackground;
    public static float globalScaleFactor = 2.0f;
    public static double originalMCScale = 2.0;
    public static long currentTime;
    public static float deltaTime;
    public static float snippetAnimationSpeed = 10f;
    public static float globalExpandSpeed = 10f;
    public static float scaleAnimationSpeed = 10f;
    public static float animScaleFactor = 1f;
    public static float globalScrollSpeed = 16f;
    public static float globalMovementSpeed = 7f;

    public static String mainMenuStyle = "Minimal";
    public static long lastFrameTime = System.nanoTime();

    public static int elementBackgroundColor = 0xFF2C2C2C;
    public static int elementBorderColor = 0xFF444444;
    public static int elementHoverBackgroundColor = 0xFF444444;
    public static int elementHoverBorderColor = 0xFF9d9d9d;
    public static int accentColor = 0xFFFFC800;
    public static int accentHoverColor = 0xFFffd94f;
    public static int accentDarkColor = 0xFF3B2D17;
    public static int accentDarkHoverColor = 0xFF523c19;
    public static int niceAccentColor = 0xFFd6f264;
    public static int niceAccentHoverColor = 0xFFe4ff78;
    public static int niceDarkAccentColor = 0xFF0b371c;
    public static int niceDarkHoverAccentColor = 0xFF0d3d20;
    public static int dangerAccentColor = 0xFFDF3E23;
    public static int dangerHoverAccentColor = 0xFFff7a7a;
    public static int dangerLightAccentColor = 0xFFff7a7a;
    public static int dangerDarkAccentColor = 0xFF3B1725;
    public static int dangerDarkHoverAccentColor = 0xFF581d35;
    public static int calmDarkAccentColor = 0xFF17253B;
    public static int calmAccentColor = 0xFF6CC4F1;

    public static int globalTextColor = 0xFFFFFFFF;
    public static int globalDarkTextColor = 0xFF888888;
    public static int globalHoverTextColor = 0xFFFFC800;
    public static int globalOuterBorder = 0xFF000000;
    public static int globalSelectionColor = 0x80FFFFFF;
    public static int globalCursorColor = 0xFFFFC800;
    public static int globalCursorAnimatedColor = 0xFFd6f264;

    public static int ModrinthBorderColor = 0xFFd6f264;
    public static int ModrinthBackgroundColor = 0xFF0b371c;
    public static int SpigotBorderColor = 0xFFFFC800;
    public static int SpigotBackgroundColor = 0xFF3B2D17;
    public static int HangarBorderColor = 0xFF6CC4F1;
    public static int HangarBackgroundColor = 0xFF17253B;

    public static int backgroundColor = 0xFF181818;
    public static int innerBackgroundColor = 0xFF222222;
    public static int innerBorderColor = 0xFF333333;
    public static int innerBackgroundSelectedColor = 0xFF555555;

    public static int terminalStatusBarColor = 0xFF555555;
    public static int terminalTextColor = 0xFFFFFFFF;
    public static int terminalTextInputColor = 0xFFFFC800;
    public static int terminalTextWarnColor = 0xFFFFA500;
    public static int terminalTextErrorColor = 0xFFFF0000;
    public static int terminalTextInfoColor = 0xFF00FF00;

    public static float colorTransitionSpeed = 10f;
    private static final Map<Integer, float[]> animatedBackgroundColorsMap = new HashMap<>();
    private static final Map<Integer, float[]> animatedBorderColorsMap = new HashMap<>();

    public static void tickTime() {
        currentTime = System.nanoTime();
        deltaTime = Math.min((currentTime - lastFrameTime) / 1_000_000_000.0f, 0.1f);
        lastFrameTime = currentTime;
    }

    private static float[] intToFloatArray(int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return new float[]{a, r, g, b};
    }

    private static int floatArrayToInt(float[] c) {
        int a = Math.round(c[0] * 255);
        int r = Math.round(c[1] * 255);
        int g = Math.round(c[2] * 255);
        int b = Math.round(c[3] * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float[] updateColor(float[] current, float[] target, float t) {
        for (int i = 0; i < 4; i++) {
            current[i] = current[i] + (target[i] - current[i]) * t;
            if (Math.abs(target[i] - current[i]) < 0.01f) {
                current[i] = target[i];
            }
        }
        return current;
    }

    public static int getElementBorderColor(int id, boolean hovered, boolean selected, boolean danger, boolean nice, boolean calm) {
        int target;
        if (danger) {
            target = hovered && selected ? dangerHoverAccentColor : selected ? dangerAccentColor : hovered ? elementHoverBorderColor : elementBorderColor;
        } else if (nice) {
            target = hovered && selected ? niceAccentHoverColor : selected ? niceAccentColor : hovered ? elementHoverBorderColor : elementBorderColor;
        } else if (calm) {
            target = selected ? calmAccentColor : hovered ? elementHoverBorderColor : elementBorderColor;
        } else {
            target = hovered && selected ? accentHoverColor : selected ? accentColor : hovered ? elementHoverBorderColor : elementBorderColor;
        }
        if (!animatedBorderColorsMap.containsKey(id)) {
            animatedBorderColorsMap.put(id, intToFloatArray(target));
        }
        float t = Math.min(colorTransitionSpeed * deltaTime, 1f);
        float[] current = animatedBorderColorsMap.get(id);
        float[] targetFloats = intToFloatArray(target);
        float[] newColorFloats = updateColor(current, targetFloats, t);
        animatedBorderColorsMap.put(id, newColorFloats);
        return floatArrayToInt(newColorFloats);
    }

    public static int getElementBackgroundColor(int id, boolean hovered, boolean selected, boolean danger, boolean nice, boolean calm) {
        int target;
        if (danger) {
            target = hovered && selected ? dangerDarkHoverAccentColor : selected ? dangerDarkAccentColor : hovered ? elementHoverBackgroundColor : elementBackgroundColor;
        } else if (nice) {
            target = hovered && selected ? niceDarkHoverAccentColor : selected ? niceDarkAccentColor : hovered ? elementHoverBackgroundColor : elementBackgroundColor;
        } else if (calm) {
            target = selected ? calmDarkAccentColor : hovered ? elementHoverBackgroundColor : elementBackgroundColor;
        } else {
            target = hovered && selected ? accentDarkHoverColor : selected ? accentDarkColor : hovered ? elementHoverBackgroundColor : elementBackgroundColor;
        }
        if (!animatedBackgroundColorsMap.containsKey(id)) {
            animatedBackgroundColorsMap.put(id, intToFloatArray(target));
        }
        float t = Math.min(colorTransitionSpeed - 0.5f * deltaTime, 1f);
        float[] current = animatedBackgroundColorsMap.get(id);
        float[] targetFloats = intToFloatArray(target);
        float[] newColorFloats = updateColor(current, targetFloats, t);
        animatedBackgroundColorsMap.put(id, newColorFloats);
        return floatArrayToInt(newColorFloats);
    }

    public static int getTextColor(boolean hovered, boolean selected) {
        return selected ? globalHoverTextColor : hovered ? globalHoverTextColor : globalTextColor;
    }
}
