package redxax.oxy.remotely.util;

import static redxax.oxy.remotely.config.Config.isDev;

public class DevUtil {
    public static void devPrint(String message) {
        if (isDev) System.out.println("DevPrint: " + message);
    }
}
