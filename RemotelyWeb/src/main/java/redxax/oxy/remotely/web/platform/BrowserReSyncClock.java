package redxax.oxy.remotely.web.platform;

import org.teavm.jso.JSBody;
import restudio.rescreen.platform.Clock;

public final class BrowserReSyncClock implements Clock {
    @Override
    public long millis() {
        return now();
    }

    @JSBody(script = "return BigInt(Math.floor(typeof performance === 'undefined' ? Date.now() : performance.now()));")
    private static native long now();
}
