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
    public static float expandAnimationSpeed = 10f;
    public static float panelExpandAnimation = 10f;
    public static float scaleAnimationSpeed = 7f;
    public static float animScaleFactor = 1f;
    public static float snippetListScrollSpeed = 10f;
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
    public static int globalCursorColor = 0xFFd6f264;

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
    public static int terminalTextInputColor = 0xFFd6f264;
    public static int terminalTextWarnColor = 0xFFFFA500;
    public static int terminalTextErrorColor = 0xFFFF0000;
    public static int terminalTextInfoColor = 0xFF00FF00;

    public static float colorTransitionSpeed = 10f;
    private static final Map<Integer, Integer> animatedBackgroundColorsMap = new HashMap<>();
    private static final Map<Integer, Integer> animatedBorderColorsMap = new HashMap<>();

    public static void tickTime() {
        currentTime = System.nanoTime();
        deltaTime = Math.min((currentTime - lastFrameTime) / 1_000_000_000.0f, 0.1f);
        lastFrameTime = currentTime;
    }

    public static int blendColor(int from, int to, float t) {
        int aFrom = (from >> 24) & 0xFF;
        int rFrom = (from >> 16) & 0xFF;
        int gFrom = (from >> 8) & 0xFF;
        int bFrom = from & 0xFF;
        int aTo = (to >> 24) & 0xFF;
        int rTo = (to >> 16) & 0xFF;
        int gTo = (to >> 8) & 0xFF;
        int bTo = to & 0xFF;
        int aNew = (int)(aFrom + (aTo - aFrom) * t);
        int rNew = (int)(rFrom + (rTo - rFrom) * t);
        int gNew = (int)(gFrom + (gTo - gFrom) * t);
        int bNew = (int)(bFrom + (bTo - bFrom) * t);
        return (aNew << 24) | (rNew << 16) | (gNew << 8) | bNew;
    }

    private static boolean isClose(int color1, int color2) {
        int threshold = 2;
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        return Math.abs(a1 - a2) < threshold &&
                Math.abs(r1 - r2) < threshold &&
                Math.abs(g1 - g2) < threshold &&
                Math.abs(b1 - b2) < threshold;
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
            animatedBorderColorsMap.put(id, target);
        }
        float t = Math.min(colorTransitionSpeed * deltaTime, 1f);
        int current = animatedBorderColorsMap.get(id);
        int newColor = blendColor(current, target, t);
        if (isClose(newColor, target)) {
            newColor = target;
        }
        animatedBorderColorsMap.put(id, newColor);
        return newColor;
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
            animatedBackgroundColorsMap.put(id, target);
        }
        float t = Math.min(colorTransitionSpeed * deltaTime, 1f);
        int current = animatedBackgroundColorsMap.get(id);
        int newColor = blendColor(current, target, t);
        if (isClose(newColor, target)) {
            newColor = target;
        }
        animatedBackgroundColorsMap.put(id, newColor);
        return newColor;
    }

    public static int getTextColor(boolean hovered, boolean selected) {
        return selected ? globalHoverTextColor : hovered ? globalHoverTextColor : globalTextColor;
    }
}
