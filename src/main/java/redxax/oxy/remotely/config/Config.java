package redxax.oxy.remotely.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Config {
    public static boolean wallpaper = false;
    public static boolean customReverseProxy = false;
    public static String proxyHost = "RedxAx.net";
    public static String proxyUser = "tunnel";
    public static boolean isDev = false;
    public static boolean enableDebugTools = true;
    public static final Path remotelyDir = Paths.get(System.getProperty("user.home"), "remotely");
    public static String mainMenuStyle = "Minimal";
    public static boolean redesignMainMenu = false;
    public static boolean scanServers = true;
    public static boolean obfuscate = true;

    public static int globalCursorColor = 0xFFFFC800;
    public static int globalCursorAnimatedColor = 0xFFd6f264;
}
