package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;

public final class DesktopServerManagementSettingsProvider implements ServerManagementSettingsProvider {
    private final Instance instance;

    public DesktopServerManagementSettingsProvider(Instance instance) {
        this.instance = instance;
    }

    @Override public boolean remote() { return instance.getBackendConfig() != null && !"LOCAL".equalsIgnoreCase(instance.getBackendConfig().type); }
    @Override public String property(String key) { return instance.getServerProperties().getProperty(key); }
    @Override public void setProperty(String key, String value) { instance.getServerProperties().setProperty(key, value); }
    @Override public void remove(String key) { instance.getServerProperties().remove(key); }
}
