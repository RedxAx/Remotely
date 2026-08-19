package redxax.oxy.remotely.data.flow;

import restudio.rescreen.platform.Clock;

public final class DesktopReSyncClock implements Clock {
    @Override
    public long millis() {
        return System.nanoTime() / 1_000_000L;
    }
}
