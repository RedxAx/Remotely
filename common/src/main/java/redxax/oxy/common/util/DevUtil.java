package redxax.oxy.common.util;

import static redxax.oxy.common.config.Config.isDev;

public class DevUtil {
    public static void devPrint(String message) {
        if (isDev) System.out.println("DevPrint: " + message);
    }
}
