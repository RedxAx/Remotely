package redxax.oxy.remotely.web.platform;

import org.teavm.jso.JSBody;
import restudio.rescreen.platform.Clock;

public final class BrowserClock implements Clock {
    @Override
    public long millis() {
        return now();
    }

    @JSBody(script = "return BigInt(Date.now());")
    private static native long now();
}
