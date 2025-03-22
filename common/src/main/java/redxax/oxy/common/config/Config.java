package redxax.oxy.common.config;

public class Config {
    public static boolean shadow = true;
    public static boolean wallpaper = false;
    public static boolean isDev = true;
    public static boolean background = true;
    public static double globalScaleFactor = 2.0;
    public static long currentTime;
    public static float deltaTime;

    public static long lastFrameTime = System.nanoTime();
    public static void TickTime() {
        currentTime = System.nanoTime();
        deltaTime = Math.min((currentTime - lastFrameTime) / 1_000_000_000.0f, 0.1f);
        lastFrameTime = currentTime;
    }

    public static int elementBackgroundColor = 0xFF2C2C2C;
    public static int elementBorderColor = 0xFF444444;
    public static int elementHoverBackgroundColor = 0xFF444444;
    public static int elementHoverBorderColor = 0xFF9d9d9d;

    public static int accentColor = 0xFFd6f264;
    public static int accentDarkColor = 0xFF0b371c;
    public static int dangerAccentColor = 0xFF3B1725;
    public static int dangerLightAccentColor = 0xFFff7a7a;
    public static int dangerDarkAccentColor = 0xFFDF3E23;
    public static int niceDarkAccentColor = 0xFF3B2D17;
    public static int niceAccentColor = 0xFFFFC800;
    public static int calmDarkAccentColor = 0xFF17253B;
    public static int calmAccentColor = 0xFF6CC4F1;

    public static int globalTextColor = 0xFFFFFFFF;
    public static int globalDarkTextColor = 0xFF888888;
    public static int globalHoverTextColor = 0xFFd6f264;
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


    public static int getElementBorderColor(boolean hovered, boolean selected) {
        return selected ? accentColor : hovered ? elementHoverBorderColor : elementBorderColor;
    }

    public static int getElementBackgroundColor(boolean hovered, boolean selected) {
        return selected ? accentDarkColor : hovered ? elementHoverBackgroundColor : elementBackgroundColor;
    }


}
