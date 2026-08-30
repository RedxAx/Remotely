package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.ui.settings.controllers.ServerFeatureSettingsProvider;

public final class BrowserServerFeatureSettingsProvider implements ServerFeatureSettingsProvider {
    private final BrowserServerConfigurationTarget target;

    public BrowserServerFeatureSettingsProvider(BrowserServerConfigurationTarget target) {
        this.target = target;
    }

    @Override public boolean local() { return false; }
    @Override public boolean lifecyclePersistent() { return true; }
    @Override public void lifecyclePersistent(boolean value) { }
    @Override public boolean restartOnCrash() { return true; }
    @Override public void restartOnCrash(boolean value) { }
    @Override public int restartDelaySeconds() { return 5; }
    @Override public void restartDelaySeconds(int value) { }
    @Override public int restartMaxAttempts() { return 3; }
    @Override public void restartMaxAttempts(int value) { }
    @Override public String property(String key, String fallback) {
        String value = target.property(key);
        return value == null ? fallback : value;
    }
    @Override public void setProperty(String key, String value) { target.property(key, value); }
}
