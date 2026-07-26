package redxax.oxy.remotely.util;

import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;

public class DevUtil {
    public static void devPrint(String message) {
        ReLog.logger(LogTypes.FLOW).source(LogSource.application("Remotely")).component(DevUtil.class).debug(message);
    }
}
