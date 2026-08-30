package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;

public final class DesktopServerFeatureSettingsProvider implements ServerFeatureSettingsProvider {
    private final Instance instance;

    public DesktopServerFeatureSettingsProvider(Instance instance) {
        this.instance = instance;
    }

    @Override public boolean local() { return instance.getBackendConfig() == null || instance.getBackendConfig().type == null || instance.getBackendConfig().type.isBlank() || "LOCAL".equalsIgnoreCase(instance.getBackendConfig().type); }
    @Override public boolean lifecyclePersistent() { return instance.isLocalLifecyclePersistent(); }
    @Override public void lifecyclePersistent(boolean value) { instance.setLocalLifecyclePersistent(value); }
    @Override public boolean restartOnCrash() { return instance.isLocalRestartOnCrash(); }
    @Override public void restartOnCrash(boolean value) { instance.setLocalRestartOnCrash(value); }
    @Override public int restartDelaySeconds() { return instance.getLocalRestartDelaySeconds(); }
    @Override public void restartDelaySeconds(int value) { instance.setLocalRestartDelaySeconds(value); }
    @Override public int restartMaxAttempts() { return instance.getLocalRestartMaxAttempts(); }
    @Override public void restartMaxAttempts(int value) { instance.setLocalRestartMaxAttempts(value); }
    @Override public String property(String key, String fallback) { return instance.getSettings().getProperty(key, fallback); }
    @Override public void setProperty(String key, String value) { instance.getSettings().setProperty(key, value); }
}
