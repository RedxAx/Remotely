package redxax.oxy.remotely.ui.settings.controllers;

public interface ServerGeneralSettingsProvider {
    String name();

    void name(String value);

    String property(String key, String fallback);

    void setProperty(String key, String value);
}
