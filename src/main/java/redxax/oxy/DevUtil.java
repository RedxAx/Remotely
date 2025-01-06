package redxax.oxy;

import static redxax.oxy.Config.isDev;

public class DevUtil {
    public static void devPrint(String message) {
        if (isDev) System.out.println("DevPrint: " + message);
    }
}
