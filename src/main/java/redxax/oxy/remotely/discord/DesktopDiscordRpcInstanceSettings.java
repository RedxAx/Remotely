package redxax.oxy.remotely.discord;

import restudio.rebase.instance.Instance;

public final class DesktopDiscordRpcInstanceSettings implements DiscordRpcSettingsController.InstanceSettings {
    private final Instance instance;

    private DesktopDiscordRpcInstanceSettings(Instance instance) {
        this.instance = instance;
    }

    public static DiscordRpcSettingsController.InstanceSettings create(Object instance) {
        return instance instanceof Instance value ? new DesktopDiscordRpcInstanceSettings(value) : null;
    }

    @Override
    public String get(String key, String fallback) {
        return instance.getSettings().getProperty(key, fallback);
    }

    @Override
    public void set(String key, String value) {
        instance.getSettings().setProperty(key, value);
    }

    @Override
    public void remove(String key) {
        instance.getSettings().remove(key);
    }
}
