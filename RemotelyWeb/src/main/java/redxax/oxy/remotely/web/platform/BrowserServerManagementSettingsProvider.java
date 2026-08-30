package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.ui.settings.controllers.ServerManagementSettingsProvider;

public final class BrowserServerManagementSettingsProvider implements ServerManagementSettingsProvider {
    private final BrowserServerConfigurationTarget target;

    public BrowserServerManagementSettingsProvider(BrowserServerConfigurationTarget target) {
        this.target = target;
    }

    @Override public boolean remote() { return true; }
    @Override public String property(String key) { return target.property(key); }
    @Override public void setProperty(String key, String value) { target.property(key, value); }
    @Override public void remove(String key) { target.removeProperty(key); }
}
