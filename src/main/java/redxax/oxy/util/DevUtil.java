package redxax.oxy.util;

import static redxax.oxy.util.Config.isDev;

public class DevUtil {
    public static void devPrint(String message) {
        if (isDev) System.out.println("DevPrint: " + message);
    }
}
