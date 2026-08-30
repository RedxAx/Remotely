package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.ui.settings.controllers.ServerGeneralSettingsProvider;

public final class BrowserServerGeneralSettingsProvider implements ServerGeneralSettingsProvider {
    private final BrowserServerConfigurationTarget target;

    public BrowserServerGeneralSettingsProvider(BrowserServerConfigurationTarget target) {
        this.target = target;
    }

    @Override public String name() { return target.name(); }
    @Override public void name(String value) { target.name(value); }
    @Override public String property(String key, String fallback) {
        String value = target.property(key);
        return value == null ? fallback : value;
    }
    @Override public void setProperty(String key, String value) { target.property(key, value); }
}
